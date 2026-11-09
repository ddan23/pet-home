package com.hnist.service;

import com.hnist.entity.Notification;
import com.hnist.common.result.PageResult;
import com.hnist.event.NotificationEvent;

import java.util.List;
import java.util.Map;

/** 站内通知服务。 */
public interface NotificationService {

    /** 写入一条通知并在提交后推送；dedupeKey 撞上 uk_notification_dedupe 时抛 DuplicateKeyException，由调用方按重复事件忽略。 */
    Notification create(NotificationEvent event);

    /** 按接收人角色允许的分类分页；categories 为空返回全部可读分类，越界分类返回 403。 */
    PageResult<Notification> listUserNotifications(Integer receiverId, String receiverRole,
                                                   List<String> categories,
                                                   List<String> types, int page, int pageSize);

    /** 管理端通知记录，固定只看 system 分类。 */
    PageResult<Notification> listAdminNotifications(String type, int page, int pageSize);

    /** 单条读取，不做归属校验，调用方需按 receiverId 比对。 */
    Notification getById(Long id);

    /** 未读通知数；receiverId 为空或非法时返回 0。 */
    int getUnreadCount(Integer receiverId);

    /** 按分类聚合未读数，键集合由接收人角色决定，无未读补 0。 */
    Map<String, Integer> getUnreadCountsByCategory(Integer receiverId, String receiverRole);

    /** 标记单条已读；不是本人的通知返回 false，不会改动任何记录。 */
    boolean markRead(Long id, Integer receiverId);

    /** 把该接收人的未读全部置为已读，条件限定 user_id = receiverId。 */
    void markAllRead(Integer receiverId);

    /** 管理端按 id 软删除，不做归属校验，权限由入口角色约束。 */
    void delete(Long id);

    /** 用户删除自己的通知；id 与 receiverId 不匹配返回 false。 */
    boolean deleteOwn(Long id, Integer receiverId);

    /** 清空已读通知，未读保留，返回实际删除条数。 */
    int deleteAllRead(Integer receiverId);

    /** 管理员公告：targetRole 有值时按角色群发，否则只发给 receiverId 指定用户。 */
    void sendAdminMessage(Integer senderId, String type, String title, String content,
                          Integer receiverId, String targetRole);
}
