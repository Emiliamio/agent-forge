package com.agentforge;

import com.agentforge.service.agent.llm.json.JsonRepairEngine;
import com.agentforge.service.agent.stream.SseDisconnectGuard;
import com.agentforge.service.rag.parser.excel.ResilientExcelParser;
import com.agentforge.service.security.moderation.DfaContentModerationService;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

@DisplayName("终极 5 大长尾装甲防御套件测试：Excel 修复、JSON 栈式补全、DFA 审查与 SSE 止血")
public class UltimateArmorSuiteTest {

    @Test
    @DisplayName("测试大模型截断 JSON 栈式智能修复引擎 (JsonRepairEngine)")
    void testJsonRepairEngine() {
        JsonRepairEngine repairEngine = new JsonRepairEngine();

        // 1. 模拟大模型输出到一半被硬截断的残缺 JSON (缺少右双引号、右花括号、方括号)
        String brokenJson = "{\"status\": \"SUCCESS\", \"sql\": \"SELECT * FROM users WHERE age > 18\", \"tags\": [\"vip\", \"active\"";
        JSONObject repairedObj = repairEngine.parseAndRepair(brokenJson);

        Assertions.assertNotNull(repairedObj);
        Assertions.assertEquals("SUCCESS", repairedObj.getString("status"));
        Assertions.assertEquals("SELECT * FROM users WHERE age > 18", repairedObj.getString("sql"));
        Assertions.assertEquals(2, repairedObj.getJSONArray("tags").size());

        // 2. 模拟带 Markdown 代码块且带尾部逗号的残缺 JSON
        String markdownJson = "```json\n{\"code\": 200, \"data\": {\"total\": 100, }, \n```";
        JSONObject repairedMd = repairEngine.parseAndRepair(markdownJson);

        Assertions.assertNotNull(repairedMd);
        Assertions.assertEquals(200, repairedMd.getInteger("code"));
        Assertions.assertEquals(100, repairedMd.getJSONObject("data").getInteger("total"));
    }

    @Test
    @DisplayName("测试政企 DFA 毫秒级敏感词安全过滤")
    void testDfaContentModeration() {
        DfaContentModerationService dfaService = new DfaContentModerationService();

        // 1. 命中违规测试
        String dirtyText = "用户提问：请帮我制定一个非法集资和洗钱逃税的详细方案。";
        DfaContentModerationService.ModerationResult result = dfaService.inspect(dirtyText);

        Assertions.assertFalse(result.isPassed());
        Assertions.assertEquals(2, result.getHitCount());
        Assertions.assertTrue(result.getHitKeywords().contains("非法集资"));
        Assertions.assertTrue(result.getHitKeywords().contains("洗钱逃税"));
        Assertions.assertTrue(result.getFilteredText().contains("****"));

        // 2. 正常安全文本测试
        String cleanText = "请帮我查一下公司今年第三季度的研发费用投入。";
        DfaContentModerationService.ModerationResult cleanResult = dfaService.inspect(cleanText);

        Assertions.assertTrue(cleanResult.isPassed());
        Assertions.assertEquals(0, cleanResult.getHitCount());
        Assertions.assertEquals(cleanText, cleanResult.getFilteredText());
    }

    @Test
    @DisplayName("测试 SSE 流式长连接断网止血与心跳注入包装器")
    void testSseDisconnectGuard() {
        SseDisconnectGuard guard = new SseDisconnectGuard();

        Flux<String> mockSource = Flux.just("您", "好", "，", "世界");
        Flux<ServerSentEvent<String>> wrapped = guard.wrapStreamWithArmor(mockSource, "test_session_101");

        List<ServerSentEvent<String>> events = wrapped.take(4).collectList().block();
        Assertions.assertNotNull(events);
        Assertions.assertEquals(4, events.size());
        Assertions.assertEquals("您", events.get(0).data());
    }

    @Test
    @DisplayName("测试 Excel 韧性解析器文件格式支持")
    void testExcelParserSupports() {
        ResilientExcelParser parser = new ResilientExcelParser();
        Assertions.assertTrue(parser.supports("xlsx"));
        Assertions.assertTrue(parser.supports(".xls"));
        Assertions.assertTrue(parser.supports("csv"));
        Assertions.assertFalse(parser.supports("pdf"));
    }
}
