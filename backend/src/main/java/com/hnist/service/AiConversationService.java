package com.hnist.service;

import com.hnist.domain.AiAdvisorResult;
import com.hnist.domain.HealthChatMessage;
import com.hnist.entity.AiChatMessage;
import com.hnist.entity.AiChatSession;
import com.hnist.vo.AiChatMessageResponse;
import com.hnist.vo.AiConversationResponse;

import java.util.List;

/** AI 养宠顾问会话服务。 */
public interface AiConversationService {

    // 同一 (userId, petId, mode) 只存在一个会话；并发首问撞唯一索引时返回已存在的那条，不抛错。
    AiChatSession getOrCreate(Integer userId, Integer petId, String mode);

    // 按 (conversationId, userId) 取会话并核对 petId 与 mode，不匹配一律 404，调用方拿到的就是可安全读写的会话。
    AiChatSession resolve(
            Integer userId, Long conversationId, Integer petId, String mode);

    // 只按 (id, userId) 定位，越权与不存在同样返回 404。
    AiChatSession require(Integer userId, Long conversationId);

    // 返回最近 12 条 COMPLETED 消息，时间正序；FAILED 占位不会被回放进模型上下文。
    List<HealthChatMessage> loadHistory(Long sessionId);

    AiChatMessage appendUserMessage(
            AiChatSession session, String content, String messageType);

    // 引用与结构化字段作为 JSON 快照落库，返回值带生成后的 messageId 供前端定位。
    AiChatMessage appendAssistantMessage(AiChatSession session, AiAdvisorResult result);

    // 模型失败时写一条 FAILED 助手消息，仅用于展示，不参与后续上下文。
    void appendFailedMessage(AiChatSession session, String content);

    // 返回最近 100 条消息，先校验会话归属。
    List<AiChatMessageResponse> listMessages(Integer userId, Long sessionId);

    // 同一事务内删除会话下的全部消息与会话本身。
    void delete(Integer userId, Long sessionId);

    AiConversationResponse toResponse(AiChatSession session);
}
