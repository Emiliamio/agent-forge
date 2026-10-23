package com.agentforge.service;

import com.agentforge.dto.*;
import com.agentforge.entity.*;
import com.agentforge.service.converter.*;
import com.agentforge.service.market.api.TemplateMarketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("XxxConverter 转换器全分支与边界覆盖测试")
public class ConverterCoverageTest {

    private void testPrivateConstructor(Class<?> clazz) throws Exception {
        Constructor<?> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object instance = constructor.newInstance();
        assertNotNull(instance);
    }

    @Test
    @DisplayName("测试所有 Converter 的私有构造器反射覆盖")
    void testPrivateConstructors() throws Exception {
        testPrivateConstructor(AgentAppConverter.class);
        testPrivateConstructor(SysTenantConverter.class);
        testPrivateConstructor(SysUserConverter.class);
        testPrivateConstructor(TokenUsageLogConverter.class);
        testPrivateConstructor(ChatSessionConverter.class);
        testPrivateConstructor(ChatMessageConverter.class);
        testPrivateConstructor(DocumentChunkConverter.class);
        testPrivateConstructor(DatasetConverter.class);
        testPrivateConstructor(DocumentConverter.class);
        testPrivateConstructor(WorkflowDefinitionConverter.class);
        testPrivateConstructor(WorkflowExecutionConverter.class);
        testPrivateConstructor(IndustryTemplateConverter.class);
    }

    @Test
    @DisplayName("测试 AgentAppConverter 全覆盖")
    void testAgentAppConverter() {
        assertNull(AgentAppConverter.toVo(null));
        assertNull(AgentAppConverter.toEntity(null));

        AgentApp entity = AgentApp.builder()
                .tenantId(1L)
                .name("app")
                .description("desc")
                .avatar("av")
                .appType("AGENT")
                .systemPrompt("prompt")
                .modelConfig("{}")
                .toolsConfig("[]")
                .datasetIds("[]")
                .status(1)
                .publicEnabled(1)
                .publicToken("tok")
                .publicTokenPrev("prev")
                .build();
        entity.setId(10L);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        var vo = AgentAppConverter.toVo(entity);
        assertNotNull(vo);
        assertEquals(10L, vo.getId());
        assertEquals("app", vo.getName());

        AgentAppRequest req = AgentAppRequest.builder()
                .name("app2")
                .description("desc2")
                .avatar("av2")
                .appType("CHATBOT")
                .systemPrompt("sp")
                .modelConfig("{}")
                .toolsConfig("[]")
                .datasetIds("[]")
                .status(1)
                .publicEnabled(0)
                .publicToken("pt")
                .publicTokenPrev("prev")
                .build();
        var e2 = AgentAppConverter.toEntity(req);
        assertNotNull(e2);
        assertEquals("app2", e2.getName());
    }

    @Test
    @DisplayName("测试 SysTenantConverter 全覆盖")
    void testSysTenantConverter() {
        assertNull(SysTenantConverter.toVo(null));

        SysTenant entity = SysTenant.builder()
                .name("tenant")
                .code("t1")
                .walletBalance(BigDecimal.valueOf(100))
                .whiteLabelConfig("{}")
                .status(1)
                .build();
        entity.setId(1L);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        var vo = SysTenantConverter.toVo(entity);
        assertNotNull(vo);
        assertEquals("tenant", vo.getName());
        assertEquals("t1", vo.getCode());
    }

    @Test
    @DisplayName("测试 SysUserConverter 全覆盖")
    void testSysUserConverter() {
        assertNull(SysUserConverter.toVo(null));

        SysUser entity = SysUser.builder()
                .tenantId(1L)
                .username("user1")
                .passwordHash("hash")
                .salt("salt")
                .realName("real")
                .email("a@b.com")
                .role("OWNER")
                .status(1)
                .build();
        entity.setId(2L);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        var vo = SysUserConverter.toVo(entity);
        assertNotNull(vo);
        assertEquals("user1", vo.getUsername());
        assertEquals("OWNER", vo.getRole());
    }

    @Test
    @DisplayName("测试 TokenUsageLogConverter 全覆盖")
    void testTokenUsageLogConverter() {
        assertNull(TokenUsageLogConverter.toVo(null));

        TokenUsageLog entity = TokenUsageLog.builder()
                .id(1L)
                .tenantId(1L)
                .appId(2L)
                .userId(3L)
                .apiKeyId(4L)
                .modelName("deepseek")
                .promptTokens(10)
                .completionTokens(20)
                .totalTokens(30)
                .isCached(false)
                .costAmount(BigDecimal.ONE)
                .durationMs(50L)
                .createdAt(LocalDateTime.now())
                .build();

        var vo = TokenUsageLogConverter.toVo(entity);
        assertNotNull(vo);
        assertEquals(30, vo.getTotalTokens());
    }

    @Test
    @DisplayName("测试 ChatSessionConverter 全覆盖")
    void testChatSessionConverter() {
        assertNull(ChatSessionConverter.toVo(null));

        ChatSession entity = ChatSession.builder()
                .id(1L)
                .tenantId(1L)
                .userId(2L)
                .appId(3L)
                .title("session")
                .messageCount(5)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        var vo = ChatSessionConverter.toVo(entity);
        assertNotNull(vo);
        assertEquals("session", vo.getTitle());
    }

    @Test
    @DisplayName("测试 ChatMessageConverter 全覆盖")
    void testChatMessageConverter() {
        assertNull(ChatMessageConverter.toVo(null));

        ChatMessage entity = ChatMessage.builder()
                .id(1L)
                .tenantId(1L)
                .sessionId(2L)
                .role("user")
                .content("hello")
                .promptTokens(5)
                .completionTokens(10)
                .totalTokens(15)
                .citationsJson("[]")
                .reasoningStepsJson("[]")
                .createdAt(LocalDateTime.now())
                .build();

        var vo = ChatMessageConverter.toVo(entity);
        assertNotNull(vo);
        assertEquals("hello", vo.getContent());
    }

    @Test
    @DisplayName("测试 DocumentChunkConverter 全覆盖")
    void testDocumentChunkConverter() {
        assertNull(DocumentChunkConverter.toVo(null));

        DocumentChunk entity = DocumentChunk.builder()
                .id(1L)
                .tenantId(1L)
                .datasetId(2L)
                .documentId(3L)
                .chunkIndex(0)
                .content("chunk content")
                .embedding("[0.1, 0.2]")
                .tokenCount(10)
                .metadata("{}")
                .tsvContent("tsv")
                .createdAt(LocalDateTime.now())
                .build();

        var vo = DocumentChunkConverter.toVo(entity);
        assertNotNull(vo);
        assertEquals("chunk content", vo.getContent());
    }

    @Test
    @DisplayName("测试 DatasetConverter 全覆盖")
    void testDatasetConverter() {
        assertNull(DatasetConverter.toVo(null));
        assertNull(DatasetConverter.toEntity(null));

        Dataset entity = Dataset.builder()
                .tenantId(1L)
                .name("ds")
                .description("desc")
                .avatar("av")
                .embeddingModel("model")
                .embeddingDim(1536)
                .chunkSize(500)
                .chunkOverlap(50)
                .status(1)
                .build();
        entity.setId(1L);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        var vo = DatasetConverter.toVo(entity);
        assertNotNull(vo);
        assertEquals("ds", vo.getName());

        DatasetRequest req = DatasetRequest.builder()
                .name("ds2")
                .description("desc2")
                .avatar("av2")
                .embeddingModel("m2")
                .embeddingDim(1024)
                .chunkSize(400)
                .chunkOverlap(40)
                .status(1)
                .build();
        var e2 = DatasetConverter.toEntity(req);
        assertNotNull(e2);
        assertEquals("ds2", e2.getName());
    }

    @Test
    @DisplayName("测试 DocumentConverter 全覆盖")
    void testDocumentConverter() {
        assertNull(DocumentConverter.toVo(null));

        Document entity = Document.builder()
                .tenantId(1L)
                .datasetId(2L)
                .name("doc.pdf")
                .filePath("/path/doc.pdf")
                .fileSize(1024L)
                .fileType("PDF")
                .charCount(500)
                .chunkCount(5)
                .parseStatus("SUCCESS")
                .errorMsg(null)
                .build();
        entity.setId(1L);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        var vo = DocumentConverter.toVo(entity);
        assertNotNull(vo);
        assertEquals("doc.pdf", vo.getName());
    }

    @Test
    @DisplayName("测试 WorkflowDefinitionConverter 全覆盖")
    void testWorkflowDefinitionConverter() {
        assertNull(WorkflowDefinitionConverter.toVo(null));
        assertNull(WorkflowDefinitionConverter.toEntity(null));

        WorkflowDefinition entity = WorkflowDefinition.builder()
                .tenantId(1L)
                .appId(2L)
                .name("wf")
                .description("desc")
                .dagJson("{}")
                .version(1)
                .status(1)
                .build();
        entity.setId(1L);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        var vo = WorkflowDefinitionConverter.toVo(entity);
        assertNotNull(vo);
        assertEquals("wf", vo.getName());

        WorkflowDefinitionRequest req = WorkflowDefinitionRequest.builder()
                .id(1L)
                .appId(2L)
                .name("wf2")
                .description("desc2")
                .dagJson("{}")
                .version(2)
                .status(1)
                .build();
        var e2 = WorkflowDefinitionConverter.toEntity(req);
        assertNotNull(e2);
        assertEquals(1L, e2.getId());
        assertEquals("wf2", e2.getName());
    }

    @Test
    @DisplayName("测试 WorkflowExecutionConverter 全覆盖")
    void testWorkflowExecutionConverter() {
        assertNull(WorkflowExecutionConverter.toVo(null));

        WorkflowExecution entity = WorkflowExecution.builder()
                .tenantId(1L)
                .workflowId(2L)
                .appId(3L)
                .triggerType("API")
                .status("SUCCESS")
                .inputSnapshot("{}")
                .outputSnapshot("{}")
                .stepDetails("[]")
                .totalDurationMs(100L)
                .errorMsg(null)
                .build();
        entity.setId(1L);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        var vo = WorkflowExecutionConverter.toVo(entity);
        assertNotNull(vo);
        assertEquals("SUCCESS", vo.getStatus());
    }

    @Test
    @DisplayName("测试 IndustryTemplateConverter 全覆盖")
    void testIndustryTemplateConverter() {
        assertNull(IndustryTemplateConverter.toVo(null));

        TemplateMarketService.IndustryTemplate tpl = TemplateMarketService.IndustryTemplate.builder()
                .templateId("fin_01")
                .name("金融审计")
                .category("金融")
                .icon("icon")
                .description("desc")
                .valueProposition("value")
                .estimatedPrice("50k")
                .dagJson("{}")
                .defaultPrompt("prompt")
                .build();

        var vo = IndustryTemplateConverter.toVo(tpl);
        assertNotNull(vo);
        assertEquals("fin_01", vo.getTemplateId());
        assertEquals("金融审计", vo.getName());
    }
}
