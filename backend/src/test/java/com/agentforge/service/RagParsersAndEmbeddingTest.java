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
import com.agentforge.service.rag.parser.excel.ResilientExcelParser;
import com.agentforge.service.rag.parser.hardened.HardenedStreamingDocumentParser;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.util.Date;
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
    @DisplayName("ResilientExcelParser: 韧性 Excel 解析与合并单元格修复全分支覆盖")
    void testResilientExcelParser() throws Exception {
        ResilientExcelParser parser = new ResilientExcelParser();

        // 1. supports 分支
        assertTrue(parser.supports("xlsx"));
        assertTrue(parser.supports("XLSX"));
        assertTrue(parser.supports(".xlsx"));
        assertTrue(parser.supports("xls"));
        assertTrue(parser.supports("csv"));
        assertFalse(parser.supports(""));
        assertFalse(parser.supports("   "));
        assertFalse(parser.supports(null));
        assertFalse(parser.supports("pdf"));

        // 2. 内存构造多 Sheet 工作簿 (包含合并单元格、公式、日期、整数/小数、布尔值、空行与空单元格)
        try (Workbook wb = new XSSFWorkbook()) {
            // Sheet 1: 核心业务表
            Sheet sheet1 = wb.createSheet("财务审计表");

            // Row 0: 表头
            Row header = sheet1.createRow(0);
            header.createCell(0).setCellValue("部门");
            header.createCell(1).setCellValue("预算");
            header.createCell(2).setCellValue("核算");
            header.createCell(3).setCellValue("公式状态");
            header.createCell(4).setCellValue("审计日期");
            header.createCell(5).setCellValue("是否合规");

            // Row 1: 数据行 1 (第 0 列作为合并主格)
            Row row1 = sheet1.createRow(1);
            row1.createCell(0).setCellValue("研发架构部");
            row1.createCell(1).setCellValue(100000.0); // 整数 numeric: num == (long) num
            row1.createCell(2).setCellValue(99999.55); // 小数 numeric
            Cell formulaCell1 = row1.createCell(3);
            formulaCell1.setCellFormula("B2+C2"); // 正常公式

            Cell dateCell = row1.createCell(4);
            dateCell.setCellValue(new Date());
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-MM-dd"));
            dateCell.setCellStyle(dateStyle);

            row1.createCell(5).setCellValue(true); // 布尔值

            // Row 2: 数据行 2 (被合并单元格覆盖的一行)
            Row row2 = sheet1.createRow(2);
            row2.createCell(1).setCellValue(50000.0);
            row2.createCell(2, CellType.BLANK); // 空 Cell
            Cell formulaErrCell = row2.createCell(3);
            formulaErrCell.setCellFormula("UNKNOWN_FUNC_XYZ(1)"); // 触发公式除零异常 -> [公式待刷新]
            row2.createCell(5).setCellValue(false);

            // 合并 (1,0) 到 (2,0)
            sheet1.addMergedRegion(new CellRangeAddress(1, 2, 0, 0));

            // Row 3: 纯空行 (用于触发 !isRowEmpty 过滤)
            Row emptyRow = sheet1.createRow(3);
            emptyRow.createCell(0).setCellValue("");
            emptyRow.createCell(1).setCellValue("   ");

            // Row 4: 不完整列数据行 (用于触发 row.size() < headers.size() 自动补齐)
            Row row4 = sheet1.createRow(4);
            row4.createCell(0).setCellValue("测试组");
            row4.createCell(1).setCellValue(20000.0);

            // Sheet 2: 纯空 Sheet (用于触发 rowDataList.isEmpty() 分支)
            wb.createSheet("空白附表");

            // Sheet 3: 第二个有效工作表 (验证多工作表遍历)
            Sheet sheet3 = wb.createSheet("补充说明");
            Row s3r0 = sheet3.createRow(0);
            s3r0.createCell(0).setCellValue("事项");
            Row s3r1 = sheet3.createRow(1);
            s3r1.createCell(0).setCellValue("全链路自动化门禁通过");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);

            ParsedDocument parsed = parser.parse(new ByteArrayInputStream(bos.toByteArray()), "audit.xlsx");
            assertNotNull(parsed);
            assertTrue(parsed.getFullText().contains("财务审计表"));
            assertTrue(parsed.getFullText().contains("研发架构部"));
            assertTrue(parsed.getFullText().contains("100000"));
            assertTrue(parsed.getFullText().contains("99999.55"));
            assertTrue(parsed.getFullText().contains("true"));
            assertTrue(parsed.getFullText().contains("false"));
            assertTrue(parsed.getFullText().contains("[公式待刷新]"));
            assertTrue(parsed.getFullText().contains("补充说明"));
            assertEquals(3, parsed.getSections().size());
        }

        // 3. 损坏输入流异常分支
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                parser.parse(new ByteArrayInputStream("corrupted excel stream".getBytes(StandardCharsets.UTF_8)), "bad.xlsx"));
        assertTrue(ex.getMessage().contains("Excel 数据清洗解析异常"));
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
        assertTrue(service.degraded());
        assertEquals("local-deterministic-sha256", service.providerName());
        float[] customDim = DefaultEmbeddingService.generateDeterministicVector("test", 768);
        assertEquals(768, customDim.length);

        // 5. 线上模型 mock 与异常回退
        dev.langchain4j.model.embedding.EmbeddingModel mockOnlineModel = mock(dev.langchain4j.model.embedding.EmbeddingModel.class);
        when(mockOnlineModel.embed(anyString())).thenReturn(dev.langchain4j.model.output.Response.from(new dev.langchain4j.data.embedding.Embedding(new float[1536])));
        when(mockOnlineModel.embedAll(anyList())).thenReturn(dev.langchain4j.model.output.Response.from(List.of(new dev.langchain4j.data.embedding.Embedding(new float[1536]))));

        ReflectionTestUtils.setField(service, "embeddingModel", mockOnlineModel);
        ReflectionTestUtils.setField(service, "isOnlineModelAvailable", true);
        assertFalse(service.degraded());
        assertEquals("openai-text-embedding-3-small", service.providerName());

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
        verify(documentMapper, timeout(3000).atLeastOnce()).updateById(any());
        verify(chunkMapper, timeout(3000).atLeast(2)).insert(any());

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
