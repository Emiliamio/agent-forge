package com.agentforge.service.agent.stream;

import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * DeepSeek-R1 风格结构化 SSE (Server-Sent Events) 事件流分发器
 * 对标 Dify / OpenAI 2025/2026 现代流式交互标准：
 * 1. 自动流式解析与剥离 <think>...</think> 深度思考链；
 * 2. 分流输出强类型事件帧：thought_start / thought_delta / thought_end / answer_delta / citations_block / done；
 * 3. 前端可实现可折叠的思维过程渲染与引用卡片动态浮现。
 */
@Service
public class StructuredSseStreamDispatcher {

    private static final Logger log = LoggerFactory.getLogger(StructuredSseStreamDispatcher.class);

    public enum EventType {
        THOUGHT_START,
        THOUGHT_DELTA,
        THOUGHT_END,
        ANSWER_DELTA,
        CITATIONS_BLOCK,
        DONE
    }

    public static class SseFrame {
        private final EventType eventType;
        private final String data;

        public SseFrame(EventType eventType, String data) {
            this.eventType = eventType;
            this.data = data;
        }

        public EventType getEventType() { return eventType; }
        public String getData() { return data; }

        /**
         * 格式化为标准 W3C SSE 报文文本
         */
        public String toSseFormat() {
            return "event: " + eventType.name().toLowerCase() + "\ndata: " + data + "\n\n";
        }
    }

    public static class StreamParseState {
        private boolean inThinking = false;
        private final StringBuilder fullThought = new StringBuilder();
        private final StringBuilder fullAnswer = new StringBuilder();

        public boolean isInThinking() { return inThinking; }
        public String getFullThought() { return fullThought.toString(); }
        public String getFullAnswer() { return fullAnswer.toString(); }
    }

    /**
     * 处理单批收到的增量 chunk 文本，并生成对应的结构化事件帧列表
     */
    public List<SseFrame> processChunk(String chunk, StreamParseState state) {
        List<SseFrame> frames = new ArrayList<>();
        if (chunk == null || chunk.isEmpty()) return frames;

        String remaining = chunk;

        if (!state.inThinking && remaining.contains("<think>")) {
            int thinkStart = remaining.indexOf("<think>");
            if (thinkStart > 0) {
                String prefix = remaining.substring(0, thinkStart);
                state.fullAnswer.append(prefix);
                frames.add(new SseFrame(EventType.ANSWER_DELTA, formatJson("text", prefix)));
            }
            state.inThinking = true;
            frames.add(new SseFrame(EventType.THOUGHT_START, formatJson("status", "thinking_started")));
            remaining = remaining.substring(thinkStart + 7);
        }

        if (state.inThinking) {
            if (remaining.contains("</think>")) {
                int thinkEnd = remaining.indexOf("</think>");
                String thoughtContent = remaining.substring(0, thinkEnd);
                if (!thoughtContent.isEmpty()) {
                    state.fullThought.append(thoughtContent);
                    frames.add(new SseFrame(EventType.THOUGHT_DELTA, formatJson("thought", thoughtContent)));
                }
                state.inThinking = false;
                frames.add(new SseFrame(EventType.THOUGHT_END, formatJson("status", "thinking_finished")));
                remaining = remaining.substring(thinkEnd + 8);
            } else {
                state.fullThought.append(remaining);
                frames.add(new SseFrame(EventType.THOUGHT_DELTA, formatJson("thought", remaining)));
                return frames;
            }
        }

        if (!remaining.isEmpty()) {
            state.fullAnswer.append(remaining);
            frames.add(new SseFrame(EventType.ANSWER_DELTA, formatJson("text", remaining)));
        }

        return frames;
    }

    /**
     * 生成引文卡片事件帧
     */
    public SseFrame createCitationsFrame(List<JSONObject> citations) {
        JSONObject data = new JSONObject();
        data.put("citations", citations);
        return new SseFrame(EventType.CITATIONS_BLOCK, data.toJSONString());
    }

    /**
     * 生成完成帧
     */
    public SseFrame createDoneFrame(int totalPromptTokens, int totalCompletionTokens) {
        JSONObject data = new JSONObject();
        data.put("status", "COMPLETED");
        data.put("promptTokens", totalPromptTokens);
        data.put("completionTokens", totalCompletionTokens);
        return new SseFrame(EventType.DONE, data.toJSONString());
    }

    private String formatJson(String key, String value) {
        JSONObject obj = new JSONObject();
        obj.put(key, value);
        return obj.toJSONString();
    }
}