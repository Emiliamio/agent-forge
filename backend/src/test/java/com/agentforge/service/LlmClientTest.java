package com.agentforge.service;

import com.agentforge.service.agent.llm.LlmClient;
import com.agentforge.service.agent.llm.LlmRequest;
import com.agentforge.service.agent.llm.LlmResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@DisplayName("LlmClientTest: 工业级多模型统一客户端与 Fallback 容灾网关单测")
public class LlmClientTest {

    @Test
    @DisplayName("init: 占位符 API Key 不初始化客户端")
    void testInit_WithPlaceholders() {
        LlmClient client = new LlmClient();
        ReflectionTestUtils.setField(client, "deepseekApiKey", "sk-placeholder");
        ReflectionTestUtils.setField(client, "openaiApiKey", "sk-placeholder");
        client.init();

        assertNull(ReflectionTestUtils.getField(client, "deepseekModel"));
        assertNull(ReflectionTestUtils.getField(client, "openaiModel"));
    }

    @Test
    @DisplayName("init: 配置有效 API Key 初始化真实模型客户端")
    void testInit_WithValidKeys() {
        LlmClient client = new LlmClient();
        ReflectionTestUtils.setField(client, "deepseekApiKey", "sk-test-deepseek-key");
        ReflectionTestUtils.setField(client, "deepseekBaseUrl", "https://api.deepseek.com/v1");
        ReflectionTestUtils.setField(client, "openaiApiKey", "sk-test-openai-key");
        ReflectionTestUtils.setField(client, "openaiBaseUrl", "https://api.openai.com/v1");
        client.init();

        assertNotNull(ReflectionTestUtils.getField(client, "deepseekModel"));
        assertNotNull(ReflectionTestUtils.getField(client, "openaiModel"));
    }

    @Test
    @DisplayName("init: 客户端配置异常进入 catch 分支且不崩溃")
    void testInit_CatchException() {
        LlmClient client = new LlmClient();
        // 设置非 placeholder 但构造参数非法引发 IllegalArgumentException
        ReflectionTestUtils.setField(client, "deepseekApiKey", "sk-valid-prefix");
        ReflectionTestUtils.setField(client, "deepseekBaseUrl", " ");
        ReflectionTestUtils.setField(client, "openaiApiKey", "sk-valid-prefix");
        ReflectionTestUtils.setField(client, "openaiBaseUrl", " ");
        assertDoesNotThrow(client::init);
    }

    @Test
    @DisplayName("generate: 主模型 DeepSeek 正常调用分支与 TokenUsage 处理")
    void testGenerate_DeepSeekSuccess() {
        LlmClient client = new LlmClient();
        ChatLanguageModel mockDeepseek = mock(ChatLanguageModel.class);

        @SuppressWarnings("unchecked")
        Response<AiMessage> mockResponse = (Response<AiMessage>) mock(Response.class);
        when(mockResponse.content()).thenReturn(AiMessage.from("DeepSeek 生成结果"));
        when(mockResponse.tokenUsage()).thenReturn(new TokenUsage(15, 25, 40));
        when(mockDeepseek.generate(anyList())).thenReturn(mockResponse);

        ReflectionTestUtils.setField(client, "deepseekModel", mockDeepseek);

        LlmRequest request = LlmRequest.builder()
                .systemPrompt("你是一名专家")
                .userPrompt("请介绍 AgentForge")
                .history(List.of(
                        new LlmRequest.ChatMessage("user", "你好"),
                        new LlmRequest.ChatMessage("assistant", "你好！请问有什么可以帮您？"),
                        new LlmRequest.ChatMessage("other", "忽略此消息")
                ))
                .build();

        LlmResponse response = client.generate(request);
        assertNotNull(response);
        assertEquals("DeepSeek 生成结果", response.getContent());
        assertEquals("deepseek-chat", response.getModelName());
        assertEquals(15, response.getPromptTokens());
        assertEquals(25, response.getCompletionTokens());
        assertEquals(40, response.getTotalTokens());
        assertFalse(response.isFallback());
    }

    @Test
    @DisplayName("generate: 主模型 DeepSeek 无 TokenUsage 时回退预估 Token")
    void testGenerate_DeepSeekNullTokenUsage() {
        LlmClient client = new LlmClient();
        ChatLanguageModel mockDeepseek = mock(ChatLanguageModel.class);

        @SuppressWarnings("unchecked")
        Response<AiMessage> mockResponse = (Response<AiMessage>) mock(Response.class);
        when(mockResponse.content()).thenReturn(AiMessage.from("回答"));
        when(mockResponse.tokenUsage()).thenReturn(null);
        when(mockDeepseek.generate(anyList())).thenReturn(mockResponse);

        ReflectionTestUtils.setField(client, "deepseekModel", mockDeepseek);

        LlmRequest request = LlmRequest.builder()
                .userPrompt("简单测试")
                .build();

        LlmResponse response = client.generate(request);
        assertNotNull(response);
        assertTrue(response.getPromptTokens() > 0);
        assertTrue(response.getCompletionTokens() > 0);
        assertFalse(response.isFallback());
    }

    @Test
    @DisplayName("generate: DeepSeek 异常切换备用 OpenAI 模型成功")
    void testGenerate_FallbackToOpenAi() {
        LlmClient client = new LlmClient();
        ChatLanguageModel mockDeepseek = mock(ChatLanguageModel.class);
        ChatLanguageModel mockOpenAi = mock(ChatLanguageModel.class);

        when(mockDeepseek.generate(anyList())).thenThrow(new RuntimeException("DeepSeek 503 Service Unavailable"));

        @SuppressWarnings("unchecked")
        Response<AiMessage> mockResponse = (Response<AiMessage>) mock(Response.class);
        when(mockResponse.content()).thenReturn(AiMessage.from("OpenAI GPT-4o 生成结果"));
        when(mockResponse.tokenUsage()).thenReturn(new TokenUsage(20, 30, 50));
        when(mockOpenAi.generate(anyList())).thenReturn(mockResponse);

        ReflectionTestUtils.setField(client, "deepseekModel", mockDeepseek);
        ReflectionTestUtils.setField(client, "openaiModel", mockOpenAi);

        LlmRequest request = LlmRequest.builder()
                .userPrompt("测试降级逻辑")
                .build();

        LlmResponse response = client.generate(request);
        assertNotNull(response);
        assertEquals("OpenAI GPT-4o 生成结果", response.getContent());
        assertEquals("gpt-4o", response.getModelName());
        assertTrue(response.isFallback());
        assertEquals(50, response.getTotalTokens());
    }

    @Test
    @DisplayName("generate: 两者皆异常降级为离线模拟响应 (带知识库引用分支)")
    void testGenerate_FallbackToSimulation_WithCitations() {
        LlmClient client = new LlmClient();
        ChatLanguageModel mockDeepseek = mock(ChatLanguageModel.class);
        ChatLanguageModel mockOpenAi = mock(ChatLanguageModel.class);

        when(mockDeepseek.generate(anyList())).thenThrow(new RuntimeException("DeepSeek 熔断"));
        when(mockOpenAi.generate(anyList())).thenThrow(new RuntimeException("OpenAI 欠费"));

        ReflectionTestUtils.setField(client, "deepseekModel", mockDeepseek);
        ReflectionTestUtils.setField(client, "openaiModel", mockOpenAi);

        LlmRequest request = LlmRequest.builder()
                .systemPrompt("参考文档如下：\n[引用 #1] 核心规范...")
                .userPrompt("系统架构是什么？")
                .build();

        LlmResponse response = client.generate(request);
        assertNotNull(response);
        assertEquals("agentforge-mock-engine", response.getModelName());
        assertTrue(response.isFallback());
        assertTrue(response.getContent().contains("基于企业知识库参考文档"));
    }

    @Test
    @DisplayName("generate: 两者皆为空降级为默认模拟响应 (无知识库引用分支)")
    void testGenerate_FallbackToSimulation_WithoutCitations() {
        LlmClient client = new LlmClient();
        // 未初始化任何模型
        LlmRequest request = LlmRequest.builder()
                .userPrompt("普通对话问询")
                .build();

        LlmResponse response = client.generate(request);
        assertNotNull(response);
        assertEquals("agentforge-mock-engine", response.getModelName());
        assertTrue(response.isFallback());
        assertTrue(response.getContent().contains("AgentForge 智能体引擎已成功调度"));
    }

    @Test
    @DisplayName("generateStream: 响应式流式打字机输出测试")
    void testGenerateStream() {
        LlmClient client = new LlmClient();
        LlmRequest request = LlmRequest.builder()
                .userPrompt("你好")
                .build();

        StepVerifier.create(client.generateStream(request))
                .expectNextCount(5) // 模拟文本被切成若干 3 字符 chunk
                .thenConsumeWhile(chunk -> chunk.length() > 0)
                .verifyComplete();
    }

    @Test
    @DisplayName("模型 POJO 与 Builder 覆盖: LlmRequest / LlmResponse / ChatMessage")
    void testPojos() {
        LlmRequest.ChatMessage msg1 = new LlmRequest.ChatMessage();
        msg1.setRole("user");
        msg1.setContent("hello");
        assertEquals("user", msg1.getRole());
        assertEquals("hello", msg1.getContent());

        LlmRequest.ChatMessage msg2 = new LlmRequest.ChatMessage("assistant", "world");
        assertEquals("assistant", msg2.getRole());
        assertEquals("world", msg2.getContent());
        assertNotNull(msg2.toString());

        LlmRequest req = new LlmRequest("sys", "user", "deepseek", "deepseek-chat", 0.5, 1024, List.of(msg1));
        assertEquals("sys", req.getSystemPrompt());
        assertEquals("user", req.getUserPrompt());
        assertEquals("deepseek", req.getProvider());
        assertEquals("deepseek-chat", req.getModelName());
        assertEquals(0.5, req.getTemperature());
        assertEquals(1024, req.getMaxTokens());
        assertEquals(1, req.getHistory().size());
        assertNotNull(req.toString());

        LlmResponse resp = new LlmResponse("content", "gpt-4o", 10, 20, 30, 100L, false);
        assertEquals("content", resp.getContent());
        assertEquals("gpt-4o", resp.getModelName());
        assertEquals(10, resp.getPromptTokens());
        assertEquals(20, resp.getCompletionTokens());
        assertEquals(30, resp.getTotalTokens());
        assertEquals(100L, resp.getDurationMs());
        assertFalse(resp.isFallback());
        assertNotNull(resp.toString());

        LlmResponse emptyResp = new LlmResponse();
        emptyResp.setContent("empty");
        assertEquals("empty", emptyResp.getContent());
    }
}
