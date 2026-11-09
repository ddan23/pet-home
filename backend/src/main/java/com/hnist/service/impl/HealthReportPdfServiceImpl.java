package com.hnist.service.impl;

import com.hnist.common.exception.BusinessException;
import com.hnist.domain.HealthCitation;
import com.hnist.entity.AiChatMessage;
import com.hnist.entity.AiChatSession;
import com.hnist.entity.Pet;
import com.hnist.mapper.AiChatMessageMapper;
import com.hnist.mapper.AiChatSessionMapper;
import com.hnist.mapper.PetMapper;
import com.hnist.service.HealthReportPdfService;
import com.hnist.support.HealthReportPdfRenderer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 把已落库的健康报告消息渲染成 PDF 字节流。 */
@Service
public class HealthReportPdfServiceImpl implements HealthReportPdfService {

    private static final String MESSAGE_TYPE_HEALTH_REPORT = "HEALTH_REPORT";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String STATUS_COMPLETED = "COMPLETED";
    // 兜底免责声明必须与生成侧的文案同义：医疗免责声明不允许因快照缺失而在导出件里消失。
    private static final String DEFAULT_DISCLAIMER =
            "AI 健康分析不能替代执业兽医的诊断和治疗。";

    private final AiChatMessageMapper messageMapper;
    private final AiChatSessionMapper sessionMapper;
    private final PetMapper petMapper;
    private final ObjectMapper objectMapper;
    private final HealthReportPdfRenderer renderer;

    public HealthReportPdfServiceImpl(AiChatMessageMapper messageMapper,
                                      AiChatSessionMapper sessionMapper,
                                      PetMapper petMapper,
                                      ObjectMapper objectMapper,
                                      HealthReportPdfRenderer renderer) {
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
        this.petMapper = petMapper;
        this.objectMapper = objectMapper;
        this.renderer = renderer;
    }

    @Override
    public byte[] export(Integer userId, Long messageId) {
        AiChatMessage message = requireReportMessage(messageId);
        // 消息表本身不带 userId，归属校验只能经 ai_chat_sessions 的 userId 条件查询完成，越权与不存在统一按 404 返回。
        AiChatSession session = requireOwnedSession(userId, message.getSessionId());
        // 三个条件缺一不可：普通问答消息、AI 失败消息都不能被当成报告导出，否则用户会下载到空壳或错误内容。
        if (!ROLE_ASSISTANT.equals(message.getRole())
                || !MESSAGE_TYPE_HEALTH_REPORT.equals(message.getMessageType())
                || !STATUS_COMPLETED.equals(message.getStatus())) {
            throw new BusinessException("该消息不是可下载的健康报告");
        }
        Pet pet = petMapper.selectByIdAndUserId(session.getPetId(), userId);
        PetSnapshot snapshot = readPetSnapshot(message.getStructuredDataJson());
        // 正文和引用读这条已落库的消息、不再调模型；宠物信息优先取 structured_data_json 里的生成时快照，
        // 这样宠物改名、生日被改或档案被删后，导出件仍与用户当时看到的报告一致。
        String petName = snapshot.petName() != null
                ? snapshot.petName() : pet == null ? "宠物" : pet.getName();
        String species = snapshot.species() != null
                ? snapshot.species() : pet == null ? null : pet.getSpecies();
        LocalDate birthDate = snapshot.birthDate() != null
                ? snapshot.birthDate() : pet == null ? null : pet.getBirthDate();
        try {
            // 渲染全程在内存（ByteArrayOutputStream）完成，不落临时文件；中文字体缺失时 renderer 直接抛 BusinessException，
            // 不降级为纯文本摘要——报告主体是中文，缺字体输出必然残缺，交付出错文件比明确报错更糟。
            return renderer.render(
                    petName, species, birthDate,
                    message.getCreatedAt() == null ? LocalDateTime.now() : message.getCreatedAt(),
                    message.getContent(),
                    readCitations(message.getCitationsJson()),
                    readDisclaimer(message.getStructuredDataJson()));
        } catch (IOException exception) {
            // IO 失败统一收敛成 500 业务异常：字体路径、文件名等服务器信息不应该经异常消息透给前端。
            throw new BusinessException(500, "健康报告 PDF 生成失败");
        }
    }

    private AiChatMessage requireReportMessage(Long messageId) {
        // 非法 ID 直接拒掉，避免用 selectById 探测任意自增值是否存在。
        if (messageId == null || messageId <= 0) {
            throw new BusinessException("健康报告消息ID不合法");
        }
        AiChatMessage message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException(404, "健康报告不存在或无权访问");
        }
        return message;
    }

    private AiChatSession requireOwnedSession(Integer userId, Long sessionId) {
        AiChatSession session = sessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            throw new BusinessException(404, "健康报告不存在或无权访问");
        }
        return session;
    }

    private List<HealthCitation> readCitations(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            // 引用是附加信息：JSON 损坏时降级为空列表，不能因为来源字段写坏就阻断整份报告下载。
            return List.of();
        }
    }

    private String readDisclaimer(String json) {
        if (json == null || json.isBlank()) {
            return DEFAULT_DISCLAIMER;
        }
        try {
            JsonNode node = objectMapper.readTree(json).path("disclaimer");
            return node.isTextual() && !node.asText().isBlank()
                    ? node.asText() : DEFAULT_DISCLAIMER;
        } catch (JsonProcessingException exception) {
            return DEFAULT_DISCLAIMER;
        }
    }

    private PetSnapshot readPetSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return new PetSnapshot(null, null, null);
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            // 快照字段缺失或格式损坏时返回空快照，交由调用方回落到 pets 实时档案。
            return new PetSnapshot(
                    text(node, "petName"),
                    text(node, "petSpecies"),
                    date(node, "petBirthDate"));
        } catch (JsonProcessingException exception) {
            return new PetSnapshot(null, null, null);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private record PetSnapshot(String petName, String species, LocalDate birthDate) {
    }
}
