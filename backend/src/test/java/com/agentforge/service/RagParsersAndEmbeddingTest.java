package com.agentforge.service;

import com.agentforge.entity.Document;
import com.agentforge.entity.DocumentChunk;
import com.agentforge.exception.BusinessException;
import com.agentforge.mapper.DocumentChunkMapper;
import com.agentforge.mapper.DocumentMapper;
import com.agentforge.service.rag.chunker.ChunkSegment;
import com.agentforge.service.rag.chunker.ChunkerEngine;
import com.agentforge.service.rag.chunker.async.AsyncDocumentChunkerService;
import com.agentforge.service.rag.embedding.DefaultEmbeddingService;
import com.agentforge.service.rag.parser.DocxDocumentParser;
import com.agentforge.service.rag.parser.ParsedDocument;
import com.agentforge.service.rag.parser.PdfDocumentParser;
import com.agentforge.service.rag.parser.hardened.HardenedStreamingDocumentParser;
import com.agentforge.service.rag.parser.sidecar.VisionSidecarParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RagParsersAndEmbeddingTest: RAG 解析器、向量服务与异步分块单测")
public class RagParsersAndEmbeddingTest {

    @Mock private DocumentMapper documentMapper;
    @Mock private DocumentChunkMapper chunkMapper;
    @Mock private ChunkerEngine chunkerEngine;
    @Mock private DefaultEmbeddingService embeddingService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    @Test
    @DisplayName("DocxDocumentParser: 正常解析段落与表格")
    void testDocxParser() throws Exception {
        DocxDocumentParser parser = new DocxDocumentParser();
        assertTrue(parser.supports("DOCX"));
        assertTrue(parser.supports("docx"));
        assertFalse(parser.supports("pdf"));
        assertFalse(parser.supports(null));

        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText("AgentForge Docx 自动化测试正文内容");

            XWPFTable table = doc.createTable();
            XWPFTableRow row = table.getRow(0);
            row.getCell(0).setText("表头1");
            row.addNewTableCell().setText("表头2");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.write(bos);

            ParsedDocument parsed = parser.parse(new ByteArrayInputStream(bos.toByteArray()), "test.docx");
            assertNotNull(parsed);
            assertTrue(parsed.getFullText().contains("AgentForge Docx"));
            assertTrue(parsed.getFullText().contains("表头1"));
            assertEquals(2, parsed.getSections().size());
        }

        // 异常分支
        assertThrows(BusinessException.class, () ->
                parser.parse(new ByteArrayInputStream("corrupt bytes".getBytes(StandardCharsets.UTF_8)), "bad.docx"));
    }

    @Test
    @DisplayName("PdfDocumentParser: 正常解析与错误隔离")
    void testPdfParser() throws Exception {
        PdfDocumentParser parser = new PdfDocumentParser();
        assertTrue(parser.supports("PDF"));
        assertFalse(parser.supports("txt"));
        assertFalse(parser.supports(null));

        try (PDDocument doc = new PDDocument()) {
            PDPage page1 = new PDPage();
            doc.addPage(page1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page1)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.showText("Page 1 AgentForge Content");
                cs.endText();
            }

            PDPage page2 = new PDPage();
            doc.addPage(page2);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page2)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.showText("Page 2 Policy Standards");
                cs.endText();
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);

            ParsedDocument parsed = parser.parse(new ByteArrayInputStream(bos.toByteArray()), "test.pdf");
            assertNotNull(parsed);
            assertEquals(2, parsed.getSections().size());
            assertTrue(parsed.getFullText().contains("Page 1"));
        }

        // 异常分支
        assertThrows(BusinessException.class, () ->
                parser.parse(new ByteArrayInputStream("corrupt pdf".getBytes(StandardCharsets.UTF_8)), "bad.pdf"));
    }

    @Test
    @DisplayName("VisionSidecarParser: GPU 侧车与回退路径")
    void testVisionSidecarParser() {
        PdfDocumentParser mockPdf = mock(PdfDocumentParser.class);
        ParsedDocument fallbackDoc = ParsedDocument.builder()
                .fullText("Fallback PDF Text")
                .sections(List.of())
                .charCount(17)
                .build();
        when(mockPdf.parse(any(), anyString())).thenReturn(fallbackDoc);

        VisionSidecarParser sidecarParser = new VisionSidecarParser(mockPdf);
        assertTrue(sidecarParser.supports("pdf"));
        assertTrue(sidecarParser.supports("png"));
        assertTrue(sidecarParser.supports("jpg"));
        assertFalse(sidecarParser.supports(null));
        assertFalse(sidecarParser.supports("doc"));

        // 1. 未启用侧车时走 fallback
        ReflectionTestUtils.setField(sidecarParser, "isSidecarEnabled", false);
        ParsedDocument parsed1 = sidecarParser.parse(new ByteArrayInputStream(new byte[]{1, 2}), "test.pdf");
        assertNotNull(parsed1);
        assertEquals("Fallback PDF Text", parsed1.getFullText());

        // 2. 启用侧车时走侧车解析分支
        ReflectionTestUtils.setField(sidecarParser, "isSidecarEnabled", true);
        ReflectionTestUtils.setField(sidecarParser, "sidecarUrl", "http://127.0.0.1:8000/api/v1/layout-parse");
        ParsedDocument parsed2 = sidecarParser.parse(new ByteArrayInputStream(new byte[]{1, 2}), "architecture.png");
        assertNotNull(parsed2);
        assertTrue(parsed2.getFullText().contains("Sidecar"));
        assertEquals(1, parsed2.getSections().size());
    }

    @Test
    @DisplayName("HardenedStreamingDocumentParser: 防爆流式解析器全分支")
    void testHardenedStreamingDocumentParser() throws Exception {
        HardenedStreamingDocumentParser parser = new HardenedStreamingDocumentParser();

        // 1. 普通文本流
        String txt = "AgentForge Armor Test Content";
        HardenedStreamingDocumentParser.ParsingReport txtReport = parser.parseStreamWithArmor(
                new ByteArrayInputStream(txt.getBytes(StandardCharsets.UTF_8)), "sample.txt", 1024 * 1024);
        assertNotNull(txtReport);
        assertEquals(1, txtReport.getTotalPages());
        assertEquals(1, txtReport.getSuccessfulPages());
        assertEquals(0, txtReport.getCorruptedPages());
        assertEquals(txt, txtReport.getParsedDocument().getFullText());

        // 2. 正常 PDF
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.showText("Armor Protected PDF Document");
                cs.endText();
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);

            HardenedStreamingDocumentParser.ParsingReport pdfReport = parser.parseStreamWithArmor(
                    new ByteArrayInputStream(bos.toByteArray()), "doc.pdf", 1024 * 1024);
            assertNotNull(pdfReport);
            assertEquals(1, pdfReport.getTotalPages());
            assertEquals(1, pdfReport.getSuccessfulPages());
            assertFalse(pdfReport.isWasEncrypted());
        }

        // 3. 超限拦截
        assertThrows(RuntimeException.class, () ->
                parser.parseStreamWithArmor(new ByteArrayInputStream("toolong".getBytes(StandardCharsets.UTF_8)), "sample.txt", 4));

        // 4. ParsingReport getter/builder
        HardenedStreamingDocumentParser.ParsingReport rep = HardenedStreamingDocumentParser.ParsingReport.builder()
                .totalPages(2)
                .successfulPages(2)
                .corruptedPages(0)
                .corruptedPageNumbers(List.of())
                .wasEncrypted(false)
                .build();
        assertEquals(2, rep.getTotalPages());
        assertEquals(0, rep.getCorruptedPages());
        assertFalse(rep.isWasEncrypted());
    }

    @Test
    @DisplayName("DefaultEmbeddingService: 确定性向量与异常边界")
    void testDefaultEmbeddingService() {
        DefaultEmbeddingService service = new DefaultEmbeddingService();
        ReflectionTestUtils.setField(service, "dimension", 1536);
        service.init();

        // 1. 空输入处理
        float[] emptyVec1 = service.embed(null);
        float[] emptyVec2 = service.embed("");
        assertEquals(1536, emptyVec1.length);
        assertEquals(1536, emptyVec2.length);

        // 2. 确定性向量生成 (相同文本输出相同向量)
        float[] vecA1 = service.embed("AgentForge CJK");
        float[] vecA2 = service.embed("AgentForge CJK");
        assertArrayEquals(vecA1, vecA2);

        // 3. 批量 Embedding
        List<float[]> emptyBatch = service.embedBatch(null);
        assertTrue(emptyBatch.isEmpty());
        List<float[]> batch = service.embedBatch(List.of("文本1", "文本2"));
        assertEquals(2, batch.size());

        // 4. 维度与静态方法
        assertEquals(1536, service.getDimension());
        float[] customDim = DefaultEmbeddingService.generateDeterministicVector("test", 768);
        assertEquals(768, customDim.length);

        // 5. 线上模型 mock 与异常回退
        dev.langchain4j.model.embedding.EmbeddingModel mockOnlineModel = mock(dev.langchain4j.model.embedding.EmbeddingModel.class);
        when(mockOnlineModel.embed(anyString())).thenReturn(dev.langchain4j.model.output.Response.from(new dev.langchain4j.data.embedding.Embedding(new float[1536])));
        when(mockOnlineModel.embedAll(anyList())).thenReturn(dev.langchain4j.model.output.Response.from(List.of(new dev.langchain4j.data.embedding.Embedding(new float[1536]))));

        ReflectionTestUtils.setField(service, "embeddingModel", mockOnlineModel);
        ReflectionTestUtils.setField(service, "isOnlineModelAvailable", true);

        float[] onlineVec = service.embed("在线文本");
        assertEquals(1536, onlineVec.length);

        List<float[]> onlineBatch = service.embedBatch(List.of("文本1"));
        assertEquals(1, onlineBatch.size());

        // 异常回退
        when(mockOnlineModel.embed(anyString())).thenThrow(new RuntimeException("API 超时"));
        when(mockOnlineModel.embedAll(anyList())).thenThrow(new RuntimeException("API 超时"));
        float[] fbVec = service.embed("超时文本");
        assertEquals(1536, fbVec.length);
        List<float[]> fbBatch = service.embedBatch(List.of("超时批量"));
        assertEquals(1, fbBatch.size());
    }

    @Test
    @DisplayName("AsyncDocumentChunkerService: 异步分块与进度查询")
    void testAsyncDocumentChunkerService() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        AsyncDocumentChunkerService service = new AsyncDocumentChunkerService(
                documentMapper, chunkMapper, chunkerEngine, embeddingService, redisTemplate);

        ParsedDocument pDoc = ParsedDocument.builder().fullText("Text").build();
        ChunkSegment seg1 = ChunkSegment.builder().chunkIndex(0).content("分块1").tokenCount(10).metadata(Map.of("k", "v")).build();
        ChunkSegment seg2 = ChunkSegment.builder().chunkIndex(1).content("分块2").tokenCount(12).build();
        when(chunkerEngine.split(any(), anyInt(), anyInt(), anyString())).thenReturn(List.of(seg1, seg2));
        when(embeddingService.embed(anyString())).thenReturn(new float[1536]);

        Document mockDoc = new Document();
        mockDoc.setId(10L);
        when(documentMapper.selectById(10L)).thenReturn(mockDoc);

        // 1. 提交异步任务
        service.submitAsyncChunkingTask(10L, "长正文内容", 1L, 2L, "doc.txt");
        Thread.sleep(150); // 等待虚拟线程执行完毕

        verify(documentMapper, atLeastOnce()).updateById(any());
        verify(chunkMapper, atLeast(2)).insert(any());

        // 2. 进度查询
        AsyncDocumentChunkerService.IngestionProgress mockProgress = AsyncDocumentChunkerService.IngestionProgress.builder()
                .documentId(10L)
                .status("COMPLETED")
                .totalChunks(2)
                .processedChunks(2)
                .progressPercent(100.0)
                .build();
        when(valueOperations.get("agentforge:doc:progress:10")).thenReturn(mockProgress);

        AsyncDocumentChunkerService.IngestionProgress progress = service.getProgress(10L);
        assertNotNull(progress);
        assertEquals("COMPLETED", progress.getStatus());
        assertEquals(100.0, progress.getProgressPercent());

        // 3. 默认进度 (未命中缓存)
        when(valueOperations.get("agentforge:doc:progress:999")).thenReturn(null);
        AsyncDocumentChunkerService.IngestionProgress defaultProg = service.getProgress(999L);
        assertEquals("QUEUED", defaultProg.getStatus());
        assertEquals(0.0, defaultProg.getProgressPercent());
    }
}
