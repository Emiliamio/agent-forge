package com.agentforge.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.ChatMessage;
import com.agentforge.entity.ChatSession;
import com.agentforge.mapper.ChatSessionMapper;
import com.agentforge.service.chat.ChatSessionService;
import com.agentforge.vo.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 企业级多轮对话会话、历史记录与 👍/👎 评价自进化闭环接口
 */
@Tag(name = "09. 多轮会话持久化与问答质量评价", description = "提供会话列表、历史消息查询与点赞/点踩反馈")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@SaCheckLogin
public class ChatSessionController {

    private final ChatSessionService chatSessionService;
    private final ChatSessionMapper sessionMapper;

    @Operation(summary = "创建或初始化新会话")
    @PostMapping("/sessions")
    public Result<ChatSession> createSession(@RequestBody CreateSessionRequest req) {
        Long userId = StpUtil.getLoginIdAsLong();
        ChatSession session = chatSessionService.createOrGetSession(req.getAppId(), userId, req.getTitle());
        return Result.success(session);
    }

    @Operation(summary = "获取当前用户的所有历史会话列表")
    @GetMapping("/sessions")
    public Result<List<ChatSession>> listSessions() {
        Long tenantId = TenantContextHolder.getTenantId();
        Long userId = StpUtil.getLoginIdAsLong();

        List<ChatSession> list = sessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getTenantId, tenantId)
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getUpdatedAt)
        );
        return Result.success(list);
    }

    @Operation(summary = "获取会话历史消息记录")
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessage>> listMessages(@PathVariable Long sessionId) {
        List<ChatMessage> list = chatSessionService.listMessages(sessionId);
        return Result.success(list);
    }

    @Operation(summary = "发送对话消息 (支持 PII 敏感脱敏与多轮持久化)")
    @PostMapping("/sessions/{sessionId}/send")
    public Result<ChatMessage> sendMessage(
            @PathVariable Long sessionId,
            @RequestBody SendMessageRequest req
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        ChatMessage assistantMsg = chatSessionService.sendQuery(sessionId, userId, req.getMessage());
        return Result.success("生成完成", assistantMsg);
    }

    @Operation(summary = "提交点赞/点踩与修改建议反馈 (用于问答自进化)")
    @PostMapping("/messages/{messageId}/feedback")
    public Result<String> submitFeedback(
            @PathVariable Long messageId,
            @RequestBody FeedbackRequest req
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        chatSessionService.submitFeedback(messageId, userId, req.getRating(), req.getFeedbackType(), req.getComment());
        return Result.success("反馈已提交，感谢您的评价！", "OK");
    }

    @Data
    public static class CreateSessionRequest {
        private Long appId;
        private String title;
    }

    @Data
    public static class SendMessageRequest {
        private String message;
    }

    @Data
    public static class FeedbackRequest {
        private Integer rating; // 1 for thumbs up, -1 for thumbs down
        private String feedbackType;
        private String comment;
    }
}
