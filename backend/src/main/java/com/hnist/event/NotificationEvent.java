package com.hnist.event;

/**
 * 通知领域事件：发布方只描述「谁该收到什么」，落库校验（分类白名单、接收人角色）由 NotificationService 负责。
 */
public record NotificationEvent(
        Integer receiverId,
        Integer senderId,
        String category,
        String type,
        String level,
        String title,
        String content,
        String relatedType,
        Long relatedId,
        String actionUrl,
        // 幂等键：配合通知表 uk_notification_dedupe(user_id, dedupe_key) 唯一索引收敛重复投递，同一事件重复发布只落一条记录。
        String dedupeKey
) {
}
