package com.hnist.service.impl;

import com.hnist.service.PetHealthQueryService;

import com.hnist.service.AiConversationService;

import com.hnist.common.constant.AiChatMode;
import com.hnist.common.exception.BusinessException;
import com.hnist.domain.AiAdvisorResult;
import com.hnist.vo.AiChatMessageResponse;
import com.hnist.vo.AiConversationResponse;
import com.hnist.domain.HealthChatMessage;
import com.hnist.domain.HealthCitation;
import com.hnist.mapper.AiChatMessageMapper;
import com.hnist.mapper.AiChatSessionMapper;
import com.hnist.entity.AiChatMessage;
import com.hnist.entity.AiChatSession;
import com.hnist.entity.Pet;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AI 养宠顾问会话服务。 */
@Service
public class AiConversationServiceImpl implements AiConversationService {

    // 只回放最近 12 条给模型：与 ai.max-history-messages 同量级，既控 token，也压缩用户可控文本的注入面。
    private static final int HISTORY_LIMIT = 12;
    // 消息接口一次最多返回 100 条，前端不做翻页，超出部分拿不到，属于当前口径边界。
    private static final int MESSAGE_PAGE_SIZE = 100;
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String TYPE_TEXT = "TEXT";
    // COMPLETED 才会进入下一轮上下文；FAILED 只是失败占位，用于展示，不能当成助手回答回放。
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final PetHealthQueryService petHealthQueryService;
    private final ObjectMapper objectMapper;

    public AiConversationServiceImpl(AiChatSessionMapper sessionMapper,
                                 AiChatMessageMapper messageMapper,
                                 PetHealthQueryService petHealthQueryService,
                                 ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.petHealthQueryService = petHealthQueryService;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public AiChatSession getOrCreate(Integer userId, Integer petId, String mode) {
        Pet pet = requireOwnedPet(userId, petId);
        if (!AiChatMode.isValid(mode)) {
            throw new BusinessException("AI会话模式不合法");
        }
        AiChatSession session = sessionMapper.selectByOwnerAndMode(userId, pet.getId(), mode);
        if (session != null) {
            return session;
        }
        AiChatSession created = new AiChatSession();
        created.setUserId(userId);
        created.setPetId(pet.getId());
        created.setMode(mode);
        try {
            sessionMapper.insert(created);
            return created;
        } catch (DuplicateKeyException exception) {
            // 并发首问会同时走到插入，唯一索引只放行一条；冲突方回查已存在会话，把并发收敛成幂等，而不是把 500 抛给用户。
            AiChatSession existing =
                    sessionMapper.selectByOwnerAndMode(userId, petId, mode);
            if (existing == null) {
                throw new BusinessException(500, "AI会话创建失败");
            }
            return existing;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public AiChatSession resolve(Integer userId, Long conversationId,
                                 Integer petId, String mode) {
        requireOwnedPet(userId, petId);
        if (!AiChatMode.isValid(mode)) {
            throw new BusinessException("AI会话模式不合法");
        }
        if (conversationId == null) {
            return getOrCreate(userId, petId, mode);
        }
        // 会话必须以 (id, userId) 查，再核对 petId 与 mode：只按 id 查就能拿别人的会话 id 读到不属于自己的对话。
        AiChatSession session =
                sessionMapper.selectByIdAndUserId(conversationId, userId);
        if (session == null || !petId.equals(session.getPetId())
                || !mode.equals(session.getMode())) {
            throw new BusinessException(404, "AI会话不存在或无权访问");
        }
        return session;
    }

    @Override
    public AiChatSession require(Integer userId, Long conversationId) {
        AiChatSession session =
                sessionMapper.selectByIdAndUserId(conversationId, userId);
        // 不存在与不属于本人返回同一句 404：区分开就等于告诉调用方这个会话 id 真实存在。
        if (session == null) {
            throw new BusinessException(404, "AI会话不存在或无权访问");
        }
        return session;
    }

    @Override
    public List<HealthChatMessage> loadHistory(Long sessionId) {
        // selectRecent 先按 id 倒序取最近 N 条，再在 SQL 外层升序返回，保证喂给模型的上下文仍是时间正序。
        List<AiChatMessage> messages = messageMapper.selectRecent(sessionId, HISTORY_LIMIT);
        List<HealthChatMessage> history = new ArrayList<>();
        for (AiChatMessage message : messages) {
            // 跳过 FAILED：上一轮的错误提示不是助手回答，回放进提示词会污染模型上下文。
            if (!STATUS_COMPLETED.equals(message.getStatus())) {
                continue;
            }
            HealthChatMessage item = new HealthChatMessage();
            item.setRole(message.getRole());
            item.setContent(message.getContent());
            history.add(item);
        }
        return history;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public AiChatMessage appendUserMessage(AiChatSession session, String content,
                                           String messageType) {
        // 用户消息不依赖模型返回，直接置 COMPLETED；模型失败时由 appendFailedMessage 另记一条 FAILED。
        return append(session, ROLE_USER, messageType, content,
                null, null, STATUS_COMPLETED);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public AiChatMessage appendAssistantMessage(AiChatSession session,
                                                AiAdvisorResult result) {
        // 引用与结构化数据以 JSON 快照落库：回放历史不必再查知识库，知识库内容后续变更也不影响已生成回答。
        String citations = writeJson(result.getCitations());
        Map<String, Object> structuredData = new LinkedHashMap<>();
        structuredData.put("recommendations", result.getRecommendations());
        structuredData.put("emergency", result.isEmergency());
        structuredData.put("refused", result.isRefused());
        structuredData.put("category", result.getCategory());
        structuredData.put("disclaimer", result.getDisclaimer());
        Pet pet = petHealthQueryService.findOwnedPet(session.getUserId(), session.getPetId());
        if (pet != null) {
            // 宠物档案同步快照：报告生成后用户改了名字或生日，历史里的这条回答也不该跟着变。
            structuredData.put("petName", pet.getName());
            structuredData.put("petSpecies", pet.getSpecies());
            structuredData.put("petBirthDate", pet.getBirthDate() == null
                    ? null : pet.getBirthDate().toString());
        }
        String structured = writeJson(structuredData);
        return append(session, ROLE_ASSISTANT, result.getMessageType(),
                result.getAnswer(), citations, structured, STATUS_COMPLETED);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void appendFailedMessage(AiChatSession session, String content) {
        // 失败占位固定 TEXT 类型：避免前端按报告或图片类型渲染出一条空内容的气泡。
        append(session, ROLE_ASSISTANT, TYPE_TEXT, content, null, null, STATUS_FAILED);
    }

    @Override
    public List<AiChatMessageResponse> listMessages(Integer userId, Long sessionId) {
        // 先校验归属再取消息，顺序不能反：否则未授权用户也能通过会话 id 读到别人的对话内容。
        require(userId, sessionId);
        return messageMapper.selectRecent(sessionId, MESSAGE_PAGE_SIZE).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(Integer userId, Long sessionId) {
        require(userId, sessionId);
        // 先删消息再删会话，避免会话还被外键引用时删除失败；两步同事务，不会出现只剩半边数据。
        messageMapper.deleteBySessionId(sessionId);
        sessionMapper.deleteByIdAndUserId(sessionId, userId);
    }

    @Override
    public AiConversationResponse toResponse(AiChatSession session) {
        return new AiConversationResponse(
                session.getId(), session.getPetId(), session.getMode(),
                session.getLastMessageAt());
    }

    private AiChatMessage append(AiChatSession session, String role, String messageType,
                                 String content, String citationsJson,
                                 String structuredDataJson, String status) {
        AiChatMessage message = new AiChatMessage();
        message.setSessionId(session.getId());
        message.setRole(role);
        message.setMessageType(messageType);
        message.setContent(content);
        message.setCitationsJson(citationsJson);
        message.setStructuredDataJson(structuredDataJson);
        message.setStatus(status);
        messageMapper.insert(message);
        // last_message_at 由 SQL NOW() 写入：会话排序以数据库时间为准，多实例部署时不会因机器时钟偏差错序。
        sessionMapper.touch(session.getId());
        session.setLastMessageAt(LocalDateTime.now());
        return message;
    }

    private Pet requireOwnedPet(Integer userId, Integer petId) {
        Pet pet = petHealthQueryService.findOwnedPet(userId, petId);
        if (pet == null) {
            throw new BusinessException(404, "宠物不存在或无权访问");
        }
        return pet;
    }

    private AiChatMessageResponse toResponse(AiChatMessage message) {
        return new AiChatMessageResponse(
                message.getId(),
                message.getRole(),
                message.getMessageType(),
                message.getContent(),
                readCitations(message.getCitationsJson()),
                readStructuredData(message.getStructuredDataJson()),
                message.getCreatedAt());
    }

    private List<HealthCitation> readCitations(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            // 单条历史 JSON 损坏只降级为空引用，不能因为一条脏数据让整个会话列表 500。
            return List.of();
        }
    }

    private Map<String, Object> readStructuredData(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            // 序列化失败按 500 抛出整轮失败：宁可这条回答不入库，也不写进半截 JSON 让历史解析长期报错。
            throw new BusinessException(500, "AI会话数据序列化失败");
        }
    }
}
