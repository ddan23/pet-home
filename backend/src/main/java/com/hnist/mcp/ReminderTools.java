package com.hnist.mcp;

import com.hnist.common.exception.BusinessException;
import com.hnist.dto.SaveHealthReminderRequest;
import com.hnist.service.HealthReminderService;
import com.hnist.vo.HealthReminderResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/** 宠物健康提醒 MCP 工具：列表只读，创建与完成属于受限写操作，删除等清理动作不向 AI 开放。 */
public class ReminderTools {

    private final HealthReminderService healthReminderService;
    private final ObjectMapper objectMapper;

    public ReminderTools(HealthReminderService healthReminderService, ObjectMapper objectMapper) {
        this.healthReminderService = healthReminderService;
        this.objectMapper = objectMapper;
    }

    /** 拉取某用户全部健康提醒，含档案推算与自定义两类。 */
    @Tool(name = "list_health_reminders",
            description = "查询指定用户的全部宠物健康提醒：包含由疫苗/驱虫/体重档案实时推算的提醒与用户自定义提醒。"
                    + "返回 reminderId（仅自定义提醒有值）、petName、type、level（OVERDUE/DUE_SOON/PLANNED 等）、"
                    + "title、dueDate 与 status。")
    public String listHealthReminders(
            @ToolParam(description = "用户 ID") Integer userId) {
        List<HealthReminderResponse> reminders = healthReminderService.listReminders(userId);
        return toJson(Map.of("reminders", reminders));
    }

    /** 创建自定义提醒，仅能挂到当前用户名下的宠物。 */
    @Tool(name = "create_health_reminder",
            description = "为用户名下宠物创建一条自定义健康提醒。petId 必须属于该 userId；"
                    + "remindAt 为 ISO 格式（yyyy-MM-ddTHH:mm）且必须晚于当前时间，留空表示不定期的长期提醒"
                    + "（创建时立即发送一次站内通知）。业务校验失败返回 code 与 message，应据此调整参数，不要盲目重试。")
    public String createHealthReminder(
            @ToolParam(description = "用户 ID") Integer userId,
            @ToolParam(description = "宠物 ID，必须属于该用户") Integer petId,
            @ToolParam(description = "提醒标题，不超过100字符") String title,
            @ToolParam(description = "提醒时间，ISO 格式 yyyy-MM-ddTHH:mm，例如 2026-11-10T09:00；长期提醒留空",
                    required = false) String remindAt,
            @ToolParam(description = "补充说明，不超过500字符", required = false) String note) {
        SaveHealthReminderRequest request = new SaveHealthReminderRequest();
        request.setPetId(petId);
        request.setTitle(title);
        request.setNote(note);
        if (remindAt != null && !remindAt.isBlank()) {
            try {
                request.setRemindAt(LocalDateTime.parse(remindAt.trim()));
            } catch (DateTimeParseException e) {
                return "{\"code\":400,\"message\":\"remindAt 格式错误，请使用 yyyy-MM-ddTHH:mm，"
                        + "例如 2026-11-10T09:00\"}";
            }
        }
        try {
            healthReminderService.createReminder(userId, request);
            return "{\"code\":200,\"message\":\"提醒已创建\"}";
        } catch (BusinessException e) {
            // 业务规则失败（宠物不属于该用户、时间已过期等）以结构化 JSON 交给模型自行调整，不算工具故障
            return toJson(Map.of("code", e.getCode(), "message", e.getMessage()));
        }
    }

    /** 完成一条自定义提醒：仅 active/notified 状态可推进，幂等防重复置位由 service 保证。 */
    @Tool(name = "complete_health_reminder",
            description = "把用户的自定义健康提醒标记为已完成。仅接受 active 或 notified 状态的提醒，"
                    + "重复完成或提醒不存在会返回业务错误码，此时应重新调用 list_health_reminders 获取最新列表。")
    public String completeHealthReminder(
            @ToolParam(description = "用户 ID") Integer userId,
            @ToolParam(description = "提醒 ID，来自 list_health_reminders 返回的 reminderId") Long reminderId) {
        try {
            healthReminderService.completeReminder(userId, reminderId);
            return "{\"code\":200,\"message\":\"提醒已完成\"}";
        } catch (BusinessException e) {
            return toJson(Map.of("code", e.getCode(), "message", e.getMessage()));
        }
    }

    private String toJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            // 不吞异常：向上抛让 MCP 协议层返回 tool 执行错误
            throw new IllegalStateException("健康提醒序列化失败", e);
        }
    }
}
