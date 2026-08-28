package com.agentforge.service.chat.impl;

import cn.hutool.core.util.StrUtil;
import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.ChatFeedback;
import com.agentforge.entity.ChatMessage;
import com.agentforge.entity.ChatSession;
import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import com.agentforge.mapper.ChatFeedbackMapper;
import com.agentforge.mapper.ChatMessageMapper;
import com.agentforge.mapper.ChatSessionMapper;
import com.agentforge.service.agent.ReActAgentService;
import com.agentforge.service.chat.ChatSessionService;
import com.agentforge.service.security.pii.PiiMaskingService;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 企业级多轮对话持久化与反馈自进化服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatFeedbackMapper feedbackMapper;
    private final ReActAgentService reActAgentService;
    private final PiiMaskingService piiMaskingService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSession createOrGetSession(Long appId, Long userId, String title) {
        Long tenantId = TenantContextHolder.getTenantId();

        ChatSession session = ChatSession.builder()
                .tenantId(tenantId)
                .userId(userId)
                .appId(appId)
                .title(StrUtil.isNotBlank(title) ? title : "新建对话 " + LocalDateTime.now().toLocalTime())
                .messageCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        sessionMapper.insert(session);
        return session;
    }

    @Override
    public List<ChatMessage> listMessages(Long sessionId) {
        Long tenantId = TenantContextHolder.getTenantId();
        return messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getTenantId, tenantId)
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getId)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessage sendQuery(Long sessionId, Long userId, String userQuery) {
        Long tenantId = TenantContextHolder.getTenantId();
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话不存在或已过期");
        }

        // 1. 持久化用户提问
        ChatMessage userMsg = ChatMessage.builder()
                .tenantId(tenantId)
                .sessionId(sessionId)
                .role("user")
                .content(userQuery)
                .createdAt(LocalDateTime.now())
                .build();
        messageMapper.insert(userMsg);

        // 2. 执行金融级 PII 敏感信息脱敏
        PiiMaskingService.MaskResult maskResult = piiMaskingService.maskSensitiveData(userQuery);

        // 3. 调度 ReAct Agent 推理
        ReActAgentService.AgentExecutionResult agentResult = reActAgentService.runAgent(
                session.getAppId(), maskResult.getMaskedText(), 5
        );

        // 4. 逆向还原 PII 掩码
        String unmaskedAnswer = piiMaskingService.unmaskResponse(agentResult.getFinalAnswer(), maskResult.getTokenMap());

        // 5. 持久化 Assistant 回答与引用
        ChatMessage assistantMsg = ChatMessage.builder()
                .tenantId(tenantId)
                .sessionId(sessionId)
                .role("assistant")
                .content(unmaskedAnswer)
                .totalTokens(agentResult.getTotalTokens())
                .citationsJson(JSON.toJSONString(agentResult.getCitations()))
                .reasoningStepsJson(JSON.toJSONString(agentResult.getSteps()))
                .createdAt(LocalDateTime.now())
                .build();
        messageMapper.insert(assistantMsg);

        // 6. 更新会话消息计数
        session.setMessageCount(session.getMessageCount() + 2);
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);

        return assistantMsg;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitFeedback(Long messageId, Long userId, int rating, String feedbackType, String comment) {
        Long tenantId = TenantContextHolder.getTenantId();

        ChatFeedback feedback = ChatFeedback.builder()
                .tenantId(tenantId)
                .messageId(messageId)
                .userId(userId)
                .rating(rating >= 0 ? 1 : -1)
                .feedbackType(feedbackType)
                .comment(comment)
                .isResolved(false)
                .createdAt(LocalDateTime.now())
                .build();

        feedbackMapper.insert(feedback);
        log.info("📊 问答质量评价已录入: msgId={}, rating={}, type={}", messageId, rating, feedbackType);
    }
}
