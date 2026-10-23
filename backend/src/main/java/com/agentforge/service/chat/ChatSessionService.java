package com.agentforge.service.chat;

import com.agentforge.entity.ChatFeedback;
import com.agentforge.entity.ChatMessage;
import com.agentforge.entity.ChatSession;
import com.agentforge.service.agent.ReActAgentService;

import java.util.List;

/**
 * 企业级多轮对话持久化与反馈自进化服务接口
 */
public interface ChatSessionService {

    /**
     * 创建或获取用户会话
     */
    ChatSession createOrGetSession(Long appId, Long userId, String title);

    /**
     * 获取指定用户的历史会话列表
     */
    List<ChatSession> listUserSessions(Long tenantId, Long userId);

    /**
     * 获取指定会话的历史消息列表
     */
    List<ChatMessage> listMessages(Long sessionId);

    /**
     * 发送问题并持久化完整 ReAct 推理轨迹与引用快照
     */
    ChatMessage sendQuery(Long sessionId, Long userId, String userQuery);

    /**
     * 提交点赞/点踩反馈与 Bad Case 批注
     */
    void submitFeedback(Long messageId, Long userId, int rating, String feedbackType, String comment);

    com.agentforge.vo.ChatSessionVo createOrGetSessionVo(Long appId, Long userId, String title);

    java.util.List<com.agentforge.vo.ChatSessionVo> listUserSessionVos(Long tenantId, Long userId);

    java.util.List<com.agentforge.vo.ChatMessageVo> listMessageVos(Long sessionId);

    com.agentforge.vo.ChatMessageVo sendQueryVo(Long sessionId, Long userId, String userQuery);
}
