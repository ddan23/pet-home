package com.hnist.controller;

import com.hnist.common.constant.AccountRole;

import com.hnist.annotation.CurrentUser;
import com.hnist.annotation.RequireRole;
import com.hnist.common.result.Result;
import com.hnist.common.constant.ConversationCategory;
import com.hnist.dto.SendAdminMessageRequest;
import com.hnist.dto.SendMessageRequest;
import com.hnist.vo.SendMessageResponse;
import com.hnist.dto.CreateConversationRequest;
import com.hnist.dto.SendConversationMessageRequest;
import com.hnist.vo.ConversationMessagePage;
import com.hnist.dto.MarkConversationReadRequest;
import com.hnist.vo.UnreadSummaryResponse;
import com.hnist.entity.Conversation;
import com.hnist.entity.Message;
import com.hnist.entity.Notification;
import com.hnist.common.result.PageResult;
import com.hnist.entity.User;
import com.hnist.service.MessageService;
import com.hnist.service.NotificationService;
import com.hnist.service.AuditLogService;
import com.hnist.sse.NotificationSseService;
import com.hnist.sse.ConversationSseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 消息与会话控制器。 */
@RestController
@RequestMapping({"/api", "/api/v1"})
public class MessageController {

    private final MessageService messageService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final NotificationSseService notificationSseService;
    private final ConversationSseService conversationSseService;

    public MessageController(MessageService messageService, NotificationService notificationService,
                             AuditLogService auditLogService,
                             NotificationSseService notificationSseService,
                             ConversationSseService conversationSseService) {
        this.messageService = messageService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.notificationSseService = notificationSseService;
        this.conversationSseService = conversationSseService;
    }

    @GetMapping({"/messages", "/notifications"})
    @RequireRole({AccountRole.USER_CODE, AccountRole.BUSINESS_CODE})
    public Result<PageResult<Notification>> listMessages(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
            @CurrentUser User user) {
        return Result.ok(notificationService.listUserNotifications(user.getId(), user.getRole(),
                splitValues(category), splitValues(type), page, pageSize));
    }

    /** 订阅通知实时推送（SSE）：连接 30 分钟超时，25 秒心跳保活，断连由回调自动清理。 */
    @GetMapping(value = "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequireRole({AccountRole.USER_CODE, AccountRole.BUSINESS_CODE})
    public SseEmitter streamNotifications(@CurrentUser User user) {
        return notificationSseService.subscribe(user.getId());
    }

    /** 允许匿名访问：未登录时以 userId=-1 查询而不是返回 401。 */
    @GetMapping({"/messages/unread", "/notifications/unread"})
    @RequireRole({AccountRole.USER_CODE, AccountRole.BUSINESS_CODE})
    public Result<Integer> getUnreadCount(@CurrentUser(required = false) User user) {
        return Result.ok(notificationService.getUnreadCount(user == null ? -1 : user.getId()));
    }

    /** 通知与私信两套未读在此聚合：total 仅统计通知，conversations 仅统计私信，grandTotal 为两者之和。 */
    @GetMapping({"/notifications/unread-summary", "/messages/unread-summary",
            "/merchant/messages/unread-summary"})
    @RequireRole({AccountRole.USER_CODE, AccountRole.BUSINESS_CODE})
    public Result<UnreadSummaryResponse> getUnreadSummary(@CurrentUser User user) {
        Map<String, Integer> counts = notificationService.getUnreadCountsByCategory(
                user.getId(), user.getRole());
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Integer> conversationCounts =
                messageService.getConversationUnreadCountsByCategory(user.getId());
        int conversations = conversationCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        return Result.ok(new UnreadSummaryResponse(
                total,
                counts.getOrDefault("community", 0),
                counts.getOrDefault("mall", 0),
                counts.getOrDefault("service", 0),
                counts.getOrDefault("health", 0),
                counts.getOrDefault("system", 0),
                conversations,
                conversationCounts.getOrDefault(
                        ConversationCategory.COMMUNITY.getCode(), 0),
                conversationCounts.getOrDefault(
                        ConversationCategory.MALL.getCode(), 0),
                conversationCounts.getOrDefault(
                        ConversationCategory.SERVICE.getCode(), 0),
                total + conversations));
    }

    /** 消息不存在或收件人非本人一律返回 404，不区分两种情况，避免暴露他人消息的存在性。 */
    @GetMapping({"/messages/{id}", "/notifications/{id}"})
    @RequireRole({AccountRole.USER_CODE, AccountRole.BUSINESS_CODE})
    public Result<Notification> getMessage(@PathVariable Long id, @CurrentUser User user) {
        Notification notification = notificationService.getById(id);
        if (notification == null || !notification.getReceiverId().equals(user.getId())) {
            return Result.fail(404, "消息不存在");
        }
        return Result.ok(notification);
    }

    @PostMapping("/messages")
    @RequireRole({AccountRole.USER_CODE, AccountRole.BUSINESS_CODE})
    public Result<SendMessageResponse> sendMessage(@Valid @RequestBody SendMessageRequest request,
                                                   @CurrentUser User user) {
        return Result.ok("发送成功", messageService.sendMessage(user.getId(), request));
    }

    /** 标记失败（不存在或非本人）返回统一的业务失败，不区分具体原因。 */
    @PutMapping({"/messages/{id}/read", "/notifications/{id}/read"})
    @RequireRole({AccountRole.USER_CODE, AccountRole.BUSINESS_CODE})
    public Result<Void> markRead(@PathVariable Long id, @CurrentUser User user) {
        if (!notificationService.markRead(id, user.getId())) {
            return Result.fail("操作失败");
        }
        return Result.ok();
    }

    @PutMapping({"/messages/readAll", "/notifications/read-all"})
    @RequireRole({AccountRole.USER_CODE, AccountRole.BUSINESS_CODE})
    public Result<Void> markAllRead(@CurrentUser User user) {
        notificationService.markAllRead(user.getId());
        return Result.ok("已全部标记为已读", null);
    }

    /** 删除非本人或不存在的消息返回 404，不暴露存在性。 */
    @DeleteMapping({"/messages/{id:\\d+}", "/notifications/{id:\\d+}"})
    @RequireRole({AccountRole.USER_CODE, AccountRole.BUSINESS_CODE})
    public Result<Void> deleteOwnNotification(@PathVariable Long id,
                                              @CurrentUser User user) {
        if (!notificationService.deleteOwn(id, user.getId())) {
            return Result.fail(404, "消息不存在");
        }
        return Result.ok("消息已删除", null);
    }

    @DeleteMapping({"/messages/read", "/notifications/read"})
    @RequireRole({AccountRole.USER_CODE, AccountRole.BUSINESS_CODE})
    public Result<Integer> deleteReadNotifications(@CurrentUser User user) {
        return Result.ok("已清空已读消息",
                notificationService.deleteAllRead(user.getId()));
    }

    @GetMapping("/conversations")
    @RequireRole(AccountRole.USER_CODE)
    public Result<PageResult<Conversation>> listConversations(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
            @CurrentUser User user) {
        return Result.ok(messageService.listConversationSummaries(
                user.getId(), splitValues(category), page, pageSize));
    }

    @GetMapping("/conversations/unread")
    @RequireRole({AccountRole.USER_CODE, AccountRole.BUSINESS_CODE})
    public Result<Integer> getConversationUnreadCount(@CurrentUser User user) {
        return Result.ok(messageService.getConversationUnreadCount(user.getId()));
    }

    /** 订阅会话消息实时推送（SSE）：连接 30 分钟超时，25 秒心跳保活，断连由回调自动清理。 */
    @GetMapping(value = "/conversations/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequireRole({AccountRole.USER_CODE, AccountRole.BUSINESS_CODE})
    public SseEmitter streamConversations(@CurrentUser User user) {
        return conversationSseService.subscribe(user.getId());
    }

    @PostMapping("/conversations")
    @RequireRole(AccountRole.USER_CODE)
    public Result<Conversation> openConversation(
            @Valid @RequestBody CreateConversationRequest request,
            @CurrentUser User user) {
        return Result.ok(messageService.openConversation(user.getId(), request));
    }

    @GetMapping("/conversations/{id}")
    @RequireRole({AccountRole.USER_CODE, AccountRole.BUSINESS_CODE})
    public Result<Conversation> getConversationDetail(@PathVariable Integer id,
                                                      @CurrentUser User user) {
        return Result.ok(messageService.getConversationForUser(id, user.getId()));
    }

    @DeleteMapping("/conversations/{id}")
    @RequireRole({AccountRole.USER_CODE, AccountRole.BUSINESS_CODE})
    public Result<Void> deleteConversation(@PathVariable Integer id,
                                           @CurrentUser User user) {
        messageService.deleteConversation(id, user.getId());
        return Result.ok("会话已删除", null);
    }

    @GetMapping("/conversations/{id}/messages")
    @RequireRole({AccountRole.USER_CODE, AccountRole.BUSINESS_CODE})
    public Result<ConversationMessagePage> listConversationMessages(
            @PathVariable Integer id,
            @RequestParam(name = "beforeId", required = false) Integer beforeId,
            @RequestParam(name = "limit", defaultValue = "30") int limit,
            @CurrentUser User user) {
        return Result.ok(messageService.listConversationMessages(
                id, user.getId(), beforeId, limit));
    }

    @PostMapping("/conversations/{id}/messages")
    @RequireRole({AccountRole.USER_CODE, AccountRole.BUSINESS_CODE})
    public Result<Message> sendConversationMessage(
            @PathVariable Integer id,
            @Valid @RequestBody SendConversationMessageRequest request,
            @CurrentUser User user) {
        return Result.ok("发送成功", messageService.sendConversationMessage(
                id, user.getId(), request));
    }

    @PutMapping("/conversations/{id}/read")
    @RequireRole({AccountRole.USER_CODE, AccountRole.BUSINESS_CODE})
    public Result<Integer> markConversationRead(
            @PathVariable Integer id,
            @RequestBody(required = false) MarkConversationReadRequest request,
            @CurrentUser User user) {
        Integer lastMessageId = request == null ? null : request.getLastMessageId();
        return Result.ok(messageService.markConversationRead(
                id, user.getId(), lastMessageId));
    }

    @GetMapping("/merchant/messages")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<PageResult<Notification>> listMerchantMessages(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
            @CurrentUser User user) {
        return Result.ok(notificationService.listUserNotifications(user.getId(), user.getRole(),
                splitValues(category), Collections.emptyList(), page, pageSize));
    }

    @GetMapping("/merchant/conversations")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<PageResult<Conversation>> listMerchantConversations(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
            @CurrentUser User user) {
        return Result.ok(messageService.listMerchantConversationSummaries(
                user.getId(), splitValues(category), page, pageSize));
    }

    @GetMapping("/admin/messages")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<PageResult<Notification>> listAdminMessages(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.ok(notificationService.listAdminNotifications(type, page, pageSize));
    }

    @GetMapping("/admin/messages/{id}")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<Notification> getAdminMessage(@PathVariable Long id) {
        return Result.ok(notificationService.getById(id));
    }

    /** 支持指定收件人（receiverId）或按角色群发（targetRole），发送动作记入审计日志。 */
    @PostMapping("/admin/messages")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<Void> sendAdminMessage(@Valid @RequestBody SendAdminMessageRequest request,
                                         @CurrentUser User user) {
        notificationService.sendAdminMessage(user.getId(), request.getType(), request.getTitle(),
                request.getContent(), request.getReceiverId(), request.getTargetRole());
        auditLogService.record(user.getId(), "NOTIFICATION_SEND", "NOTIFICATION", null,
                java.util.Map.of("type", request.getType() == null ? "system" : request.getType(),
                        "targetRole", request.getTargetRole() == null ? "" : request.getTargetRole()));
        return Result.ok("发布成功", null);
    }

    @DeleteMapping("/admin/messages/{id}")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<Void> deleteAdminMessage(@PathVariable Long id, @CurrentUser User operator) {
        notificationService.delete(id);
        auditLogService.record(operator.getId(), "NOTIFICATION_DELETE", "NOTIFICATION", id, null);
        return Result.ok();
    }

    /** category/type 支持逗号分隔多值过滤。 */
    private List<String> splitValues(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
