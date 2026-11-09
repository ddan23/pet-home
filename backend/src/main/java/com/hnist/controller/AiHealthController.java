package com.hnist.controller;

import com.hnist.common.constant.AccountRole;

import com.hnist.service.AiAdvisorStreamService;
import com.hnist.service.AiConversationService;
import com.hnist.service.HealthReminderService;
import com.hnist.service.HealthReportPdfService;
import com.hnist.annotation.CurrentUser;
import com.hnist.annotation.RequireRole;
import com.hnist.common.result.Result;
import com.hnist.vo.AiChatMessageResponse;
import com.hnist.vo.AiConversationResponse;
import com.hnist.dto.AiStreamChatRequest;
import com.hnist.dto.AiStreamRecommendationRequest;
import com.hnist.dto.AiStreamReportRequest;
import com.hnist.dto.SaveHealthReminderRequest;
import com.hnist.vo.HealthReminderResponse;
import com.hnist.entity.User;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 猫狗健康 AI 养宠顾问控制器。 */
@RestController
@RequestMapping({"/api/ai/health", "/api/v1/ai/health"})
@RequireRole(AccountRole.USER_CODE)
public class AiHealthController {

    private final AiAdvisorStreamService streamService;
    private final AiConversationService conversationService;
    private final HealthReminderService healthReminderService;
    private final HealthReportPdfService healthReportPdfService;

    public AiHealthController(AiAdvisorStreamService streamService,
                              AiConversationService conversationService,
                              HealthReminderService healthReminderService,
                              HealthReportPdfService healthReportPdfService) {
        this.streamService = streamService;
        this.conversationService = conversationService;
        this.healthReminderService = healthReminderService;
        this.healthReportPdfService = healthReportPdfService;
    }

    /** 流式回复通过 SSE 分片推送，响应头已禁用代理缓冲，保证增量分片实时到达客户端。 */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody AiStreamChatRequest request,
                           @CurrentUser User user,
                           HttpServletResponse response) {
        prepareStreamResponse(response);
        return streamService.streamChat(user.getId(), request);
    }

    @PostMapping(value = "/report/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter report(@Valid @RequestBody AiStreamReportRequest request,
                             @CurrentUser User user,
                             HttpServletResponse response) {
        prepareStreamResponse(response);
        return streamService.streamReport(user.getId(), request);
    }

    @PostMapping(value = "/recommend/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter recommend(
            @Valid @RequestBody AiStreamRecommendationRequest request,
            @CurrentUser User user,
            HttpServletResponse response) {
        prepareStreamResponse(response);
        return streamService.streamRecommendation(user.getId(), request);
    }

    @GetMapping("/conversations")
    public Result<AiConversationResponse> conversation(
            @RequestParam Integer petId,
            @RequestParam String mode,
            @CurrentUser User user) {
        return Result.ok(conversationService.toResponse(
                conversationService.getOrCreate(user.getId(), petId, mode)));
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<List<AiChatMessageResponse>> messages(
            @PathVariable Long id, @CurrentUser User user) {
        return Result.ok(conversationService.listMessages(user.getId(), id));
    }

    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(@PathVariable Long id,
                                           @CurrentUser User user) {
        conversationService.delete(user.getId(), id);
        return Result.ok("AI会话已清空", null);
    }

    /** 报告以附件形式下载，响应禁用缓存（no-store），避免健康数据残留在中间缓存。 */
    @GetMapping("/reports/{messageId}/pdf")
    public ResponseEntity<byte[]> downloadHealthReport(
            @PathVariable Long messageId, @CurrentUser User user) {
        byte[] pdf = healthReportPdfService.export(user.getId(), messageId);
        String filename = "health-report-" + messageId + ".pdf";
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }

    @GetMapping("/reminders")
    public Result<List<HealthReminderResponse>> listReminders(@CurrentUser User user) {
        return Result.ok(healthReminderService.listReminders(user.getId()));
    }

    @PostMapping("/reminders")
    public Result<Void> createReminder(@Valid @RequestBody SaveHealthReminderRequest request,
                                       @CurrentUser User user) {
        healthReminderService.createReminder(user.getId(), request);
        return Result.ok("提醒已添加", null);
    }

    @PutMapping("/reminders/{id}")
    public Result<Void> updateReminder(@PathVariable Long id,
                                       @Valid @RequestBody SaveHealthReminderRequest request,
                                       @CurrentUser User user) {
        healthReminderService.updateReminder(user.getId(), id, request);
        return Result.ok("提醒已更新", null);
    }

    @PutMapping("/reminders/{id}/complete")
    public Result<Void> completeReminder(@PathVariable Long id,
                                         @CurrentUser User user) {
        healthReminderService.completeReminder(user.getId(), id);
        return Result.ok("提醒已完成", null);
    }

    @DeleteMapping("/reminders/{id}")
    public Result<Void> deleteReminder(@PathVariable Long id,
                                       @CurrentUser User user) {
        healthReminderService.deleteReminder(user.getId(), id);
        return Result.ok("提醒已删除", null);
    }

    /** 禁用缓存与 Nginx 缓冲（X-Accel-Buffering: no），避免网关把 SSE 分片攒批后再发。 */
    private void prepareStreamResponse(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
    }
}
