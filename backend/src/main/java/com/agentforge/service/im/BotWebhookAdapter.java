package com.agentforge.service.im;

import com.agentforge.service.agent.ReActAgentService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 企业微信 / 飞书 / 钉钉 多端机器人消息网关适配器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotWebhookAdapter {

    private final ReActAgentService reActAgentService;

    /**
     * 处理飞书开放平台事件回调 (自动解包提问并调用 Agent 响应)
     */
    public Map<String, Object> handleFeishuEvent(String jsonBody) {
        JSONObject event = JSON.parseObject(jsonBody);

        // 1. URL 挑战验证 (Challenge Check)
        if (event != null && event.containsKey("challenge")) {
            return Map.of("challenge", event.getString("challenge"));
        }

        // 2. 提取用户提问并异步调用 Agent
        if (event != null && event.containsKey("event")) {
            JSONObject eventObj = event.getJSONObject("event");
            if (eventObj != null && eventObj.containsKey("message")) {
                String userQuery = eventObj.getJSONObject("message").getString("content");
                Long agentId = 1L; // 默认绑定智能体

                ReActAgentService.AgentExecutionResult result = reActAgentService.runAgent(agentId, userQuery, 5);
                log.info("飞书机器人应答完成: query={}, answerLength={}", userQuery, result.getFinalAnswer().length());
            }
        }

        return Map.of("code", 0, "msg", "success");
    }
}
