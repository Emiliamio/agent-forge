package com.agentforge.service;

import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.Dataset;
import com.agentforge.entity.Document;
import com.agentforge.entity.DocumentChunk;
import com.agentforge.exception.BusinessException;
import com.agentforge.mapper.DatasetMapper;
import com.agentforge.mapper.DocumentChunkMapper;
import com.agentforge.mapper.DocumentMapper;
import com.agentforge.service.rag.chunker.ChunkSegment;
import com.agentforge.service.rag.chunker.ChunkerEngine;
import com.agentforge.service.rag.embedding.EmbeddingService;
import com.agentforge.service.rag.impl.DatasetServiceImpl;
import com.agentforge.service.rag.impl.DocumentServiceImpl;
import com.agentforge.service.rag.parser.DocumentParser;
import com.agentforge.service.rag.parser.DocumentParserFactory;
import com.agentforge.service.rag.parser.ParsedDocument;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DocumentAndDatasetServiceTest: 知识库与文档增删改查单测")
public class DocumentAndDatasetServiceTest {

    @Mock private DatasetMapper datasetMapper;
    @Mock private DocumentMapper documentMapper;
    @Mock private DocumentChunkMapper chunkMapper;
    @Mock private DocumentParserFactory parserFactory;
    @Mock private ChunkerEngine chunkerEngine;
    @Mock private EmbeddingService embeddingService;
    @Mock private DocumentParser documentParser;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        TableInfoHelper.remove(Dataset.class);
        TableInfoHelper.remove(Document.class);
        MybatisConfiguration config = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(config, "");
        assistant.setCurrentNamespace("com.agentforge.mapper.DatasetMapper");
        TableInfoHelper.initTableInfo(assistant, Dataset.class);
        MapperBuilderAssistant docAssistant = new MapperBuilderAssistant(config, "");
        docAssistant.setCurrentNamespace("com.agentforge.mapper.DocumentMapper");
        TableInfoHelper.initTableInfo(docAssistant, Document.class);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @AfterAll
    static void clearMybatisMetadata() {
        TableInfoHelper.remove(Document.class);
        TableInfoHelper.remove(Dataset.class);
        TableInfoHelper.remove(com.agentforge.entity.DocumentChunk.class);
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("DatasetServiceImpl: 创建、查询与级联删除")
    void testDatasetService() {
        DatasetServiceImpl service = new DatasetServiceImpl(documentMapper, chunkMapper);
        ReflectionTestUtils.setField(service, "baseMapper", datasetMapper);

        // 1. 创建数据集
        Dataset newDs = new Dataset();
        newDs.setName("财务制度库");
        Dataset created = service.createDataset(newDs);
        assertNotNull(created);
        assertEquals(1L, created.getTenantId());
        assertEquals(1536, created.getEmbeddingDim());
        assertEquals(500, created.getChunkSize());
        assertEquals(50, created.getChunkOverlap());
        verify(datasetMapper, atLeastOnce()).insert(any(Dataset.class));

        // 2. 级联删除 - 跨租户拦截
        Dataset otherTenantDs = new Dataset();
        otherTenantDs.setId(10L);
        otherTenantDs.setTenantId(2L);
        when(datasetMapper.selectById(10L)).thenReturn(otherTenantDs);
        assertThrows(BusinessException.class, () -> service.deleteDataset(10L));

        // 3. 级联删除 - 正常删除清理关联文档与切片
        Dataset myDs = new Dataset();
        myDs.setId(1L);
        myDs.setTenantId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(myDs);
        when(datasetMapper.deleteById(1L)).thenReturn(1);

        service.deleteDataset(1L);
        verify(chunkMapper, atLeastOnce()).delete(any(LambdaQueryWrapper.class));
        verify(documentMapper, atLeastOnce()).delete(any(LambdaQueryWrapper.class));

        when(datasetMapper.selectPage(any(), any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10));
        assertNotNull(service.createDataset(com.agentforge.dto.DatasetRequest.builder().name("财务制度库").embeddingDim(1536).chunkSize(500).chunkOverlap(50).build()));
        assertNotNull(service.listDatasetVos("", 1, 10));
    }

    @Test
    @DisplayName("DocumentServiceImpl: 上传、解析流程与删除")
    void testDocumentService() throws Exception {
        DocumentServiceImpl service = new DocumentServiceImpl(
                datasetMapper, chunkMapper, parserFactory, chunkerEngine, embeddingService);
        ReflectionTestUtils.setField(service, "baseMapper", documentMapper);

        // 1. 上传文件为空校验
        assertThrows(BusinessException.class, () -> service.uploadAndParse(1L, null));
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.txt", "text/plain", new byte[0]);
        assertThrows(BusinessException.class, () -> service.uploadAndParse(1L, emptyFile));

        // 2. 上传成功触发解析
        Dataset ds = new Dataset();
        ds.setId(1L);
        ds.setTenantId(1L);
        ds.setChunkSize(500);
        ds.setChunkOverlap(50);
        when(datasetMapper.selectById(1L)).thenReturn(ds);

        Document existingDoc = Document.builder()
                .tenantId(1L)
                .datasetId(1L)
                .name("policy.txt")
                .fileType("TXT")
                .build();
        existingDoc.setId(100L);
        when(documentMapper.selectById(any())).thenReturn(existingDoc);

        MockMultipartFile file = new MockMultipartFile("file", "policy.txt", "text/plain", "北京出差住宿标准500元".getBytes(StandardCharsets.UTF_8));
        Document doc = service.uploadAndParse(1L, file);
        assertNotNull(doc);
        assertEquals("policy.txt", doc.getName());
        assertEquals("PENDING", doc.getParseStatus());

        // 3. 执行同步解析流水线
        when(parserFactory.getParser(anyString())).thenReturn(documentParser);
        ParsedDocument parsedDoc = ParsedDocument.builder()
                .fullText("北京出差住宿标准500元")
                .charCount(13)
                .sections(List.of())
                .build();
        when(documentParser.parse(any(), anyString())).thenReturn(parsedDoc);

        ChunkSegment seg = ChunkSegment.builder()
                .chunkIndex(0)
                .content("北京出差住宿标准500元")
                .tokenCount(13)
                .metadata(Map.of("page", 1))
                .build();
        when(chunkerEngine.split(any(), anyInt(), anyInt(), anyString())).thenReturn(List.of(seg));
        when(embeddingService.embedBatch(anyList())).thenReturn(List.of(new float[1536]));

        service.processDocument(100L, file.getBytes(), "policy.txt", "TXT");
        verify(chunkMapper, atLeastOnce()).insert(any(DocumentChunk.class));
        verify(documentMapper, atLeastOnce()).updateById(any(Document.class));

        // 4. 删除文档
        when(documentMapper.deleteById(100L)).thenReturn(1);
        service.deleteDocument(100L);
        verify(chunkMapper, atLeastOnce()).delete(any(LambdaQueryWrapper.class));

        when(documentMapper.selectPage(any(), any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10));
        assertNotNull(service.listDocumentVos(1L, 1, 10));
    }
}
