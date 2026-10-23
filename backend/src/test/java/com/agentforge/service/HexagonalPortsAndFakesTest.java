package com.agentforge.service;

import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.AgentApp;
import com.agentforge.entity.Dataset;
import com.agentforge.entity.Document;
import com.agentforge.fake.FakeChatModelPort;
import com.agentforge.fake.FakeDocumentStoragePort;
import com.agentforge.fake.FakeEmbeddingPort;
import com.agentforge.mapper.AgentAppMapper;
import com.agentforge.mapper.DatasetMapper;
import com.agentforge.mapper.DocumentChunkMapper;
import com.agentforge.mapper.DocumentMapper;
import com.agentforge.service.agent.ReActAgentService;
import com.agentforge.service.rag.RagService;
import com.agentforge.service.rag.chunker.ChunkerEngine;
import com.agentforge.service.rag.impl.DocumentServiceImpl;
import com.agentforge.service.rag.parser.DocumentParserFactory;
import com.agentforge.tools.ToolRegistry;
import com.agentforge.vo.AgentExecutionResult;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("R6: 六边形端口与测试替身 (Ports & Fakes) 闭环集成测试")
public class HexagonalPortsAndFakesTest {

    @Mock private AgentAppMapper agentAppMapper;
    @Mock private ToolRegistry toolRegistry;
    @Mock private RagService ragService;
    @Mock private DatasetMapper datasetMapper;
    @Mock private DocumentMapper documentMapper;
    @Mock private DocumentChunkMapper chunkMapper;
    @Mock private DocumentParserFactory parserFactory;
    @Mock private ChunkerEngine chunkerEngine;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        com.baomidou.mybatisplus.core.MybatisConfiguration config = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(config, "");
        assistant.setCurrentNamespace("com.agentforge.mapper.DocumentMapper");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Document.class);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("验证 ChatModelPort 注入 FakeChatModelPort: 无需 Mock 三方 SDK 即可完成 Agent 推理状态机闭环")
    void testChatModelPortWithFake() {
        FakeChatModelPort fakeChatModel = new FakeChatModelPort("Thought: 直出答案。\nFinal Answer: 端口抽象闭环验证成功。");
        ReActAgentService agentService = new ReActAgentService(fakeChatModel, toolRegistry, ragService, agentAppMapper);

        AgentApp app = new AgentApp();
        app.setId(10L);
        app.setStatus(1);
        app.setName("HexagonalTestAgent");
        app.setSystemPrompt("You are a test assistant.");
        when(agentAppMapper.selectById(10L)).thenReturn(app);

        AgentExecutionResult result = agentService.runAgent(10L, "你好，请验证端口接口", 3);

        assertNotNull(result);
        assertEquals("端口抽象闭环验证成功。", result.getFinalAnswer());
        assertTrue(fakeChatModel.getCallCount() >= 1, "FakeChatModelPort 应当被真实调度至少 1 次");
    }

    @Test
    @DisplayName("验证 EmbeddingPort 注入 FakeEmbeddingPort: 确定性高维向量计算")
    void testEmbeddingPortWithFake() {
        FakeEmbeddingPort fakeEmbedding = new FakeEmbeddingPort(1536);

        assertEquals(1536, fakeEmbedding.getDimension());
        assertTrue(fakeEmbedding.degraded());
        assertEquals("fake-embedding-deterministic", fakeEmbedding.providerName());

        float[] singleVec = fakeEmbedding.embed("AgentForge 向量化测试");
        assertNotNull(singleVec);
        assertEquals(1536, singleVec.length);

        List<float[]> batchVecs = fakeEmbedding.embedBatch(List.of("文本A", "文本B"));
        assertEquals(2, batchVecs.size());
        assertEquals(1536, batchVecs.get(0).length);
        assertEquals(1536, batchVecs.get(1).length);
        assertEquals(3, fakeEmbedding.getCallCount());
    }

    @Test
    @DisplayName("验证 DocumentStoragePort 注入 FakeDocumentStoragePort: 文档对象存储闭环")
    void testDocumentStoragePortWithFake() {
        FakeDocumentStoragePort fakeStorage = new FakeDocumentStoragePort();

        DocumentServiceImpl docService = new DocumentServiceImpl(
                datasetMapper, chunkMapper, parserFactory, chunkerEngine, null
        );
        ReflectionTestUtils.setField(docService, "baseMapper", documentMapper);
        ReflectionTestUtils.setField(docService, "documentStoragePort", fakeStorage);

        Dataset ds = new Dataset();
        ds.setId(1L);
        ds.setTenantId(1L);
        when(datasetMapper.selectById(1L)).thenReturn(ds);

        com.agentforge.service.rag.parser.DocumentParser mockParser = org.mockito.Mockito.mock(com.agentforge.service.rag.parser.DocumentParser.class);
        when(mockParser.parse(any(), any())).thenReturn(com.agentforge.service.rag.parser.ParsedDocument.builder().fullText("Mocked content").charCount(14).build());
        when(parserFactory.getParser(any())).thenReturn(mockParser);

        Document doc = Document.builder()
                .tenantId(1L)
                .datasetId(1L)
                .name("contract.pdf")
                .filePath("local://uploads/contract.pdf")
                .build();
        doc.setId(1001L);

        org.mockito.Mockito.doAnswer(invocation -> {
            Document arg = invocation.getArgument(0);
            arg.setId(1001L);
            return 1;
        }).when(documentMapper).insert(any(Document.class));

        org.mockito.Mockito.doReturn(doc).when(documentMapper).selectById(any());
        org.mockito.Mockito.doReturn(1).when(documentMapper).deleteById(any());

        MockMultipartFile file = new MockMultipartFile(
                "file", "contract.pdf", "application/pdf", "PDF-CONTENT-SAMPLE".getBytes(StandardCharsets.UTF_8)
        );

        Document createdDoc = docService.uploadAndParse(1L, file);
        assertNotNull(createdDoc);
        assertEquals(1001L, createdDoc.getId());
        assertTrue(fakeStorage.size() >= 1, "文件上传后应通过 DocumentStoragePort 真实持久化");
        assertTrue(fakeStorage.exists("1/1001"));

        // 验证删除文档时级联清理存储
        docService.deleteDocument(createdDoc.getId());
        assertEquals(0, fakeStorage.size(), "文档删除后应通过 DocumentStoragePort 真实删除对应文件");
    }
}
