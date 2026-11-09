package com.hnist.service.impl;

import com.hnist.common.constant.AccountRole;
import com.hnist.common.constant.AccountStatus;
import com.hnist.common.constant.NotificationCategory;
import com.hnist.common.exception.BusinessException;
import com.hnist.mapper.NotificationMapper;
import com.hnist.mapper.UserMapper;
import com.hnist.vo.NotificationCategoryCount;
import com.hnist.entity.Notification;
import com.hnist.common.result.PageResult;
import com.hnist.entity.User;
import com.hnist.event.NotificationEvent;
import com.hnist.sse.NotificationSseService;
import com.hnist.service.NotificationService;
import com.hnist.common.util.PageUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 站内通知的写入、查询、已读与实时推送实现。 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final NotificationSseService sseService;

    public NotificationServiceImpl(NotificationMapper notificationMapper, UserMapper userMapper,
                                   NotificationSseService sseService) {
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
        this.sseService = sseService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Notification create(NotificationEvent event) {
        // receiverId 为空或非正数说明上游事件构造有误：直接失败，避免落一条谁都不显示的孤儿通知。
        if (event.receiverId() == null || event.receiverId() <= 0) {
            throw new BusinessException("通知接收人不能为空");
        }
        String category = defaultText(event.category(), NotificationCategory.SYSTEM.getCode());
        String type = defaultText(event.type(), "system");
        // category 与 type 必须落在 NotificationCategory 白名单内：前端按 category 分 tab、按 type 决定文案与跳转，非法组合会写进没有任何入口的分类。
        if (!NotificationCategory.isValidPair(category, type)) {
            throw new BusinessException("通知分类与类型不匹配");
        }
        User receiver = userMapper.selectById(event.receiverId());
        if (receiver == null || !AccountStatus.NORMAL.getCode().equals(receiver.getStatus())) {
            throw new BusinessException("通知接收人不存在或不可用");
        }
        // allowsRole 决定谁能收到哪类通知：社区与健康通知只给普通用户，商城/服务通知普通用户和商家都收，商家账号不该收到社区互动提醒。
        NotificationCategory categoryEnum = NotificationCategory.fromCode(category);
        if (categoryEnum == null || !categoryEnum.allowsRole(receiver.getRole())) {
            throw new BusinessException(403, "当前通知分类不适用于接收人角色");
        }
        Notification notification = new Notification();
        notification.setReceiverId(event.receiverId());
        notification.setSenderId(event.senderId());
        notification.setCategory(category);
        notification.setType(type);
        // level/title/content 在 notifications 表都是 NOT NULL：缺省值在这里补齐，事件侧漏传不会让插入失败。
        notification.setLevel(defaultText(event.level(), "info"));
        notification.setTitle(defaultText(event.title(), "消息通知"));
        notification.setContent(defaultText(event.content(), ""));
        notification.setRelatedType(event.relatedType());
        notification.setRelatedId(event.relatedId());
        // actionUrl 由 safeActionUrl 收窄为站内相对路径：前端点击“查看相关页面”直接 router.push，放行 http:// 或 javascript: 就等于开放重定向。
        notification.setActionUrl(safeActionUrl(event.actionUrl()));
        // dedupeKey 原样落库，由唯一索引 uk_notification_dedupe(user_id, dedupe_key) 收敛重复：
        // 同一事件重复投递在此抛 DuplicateKeyException，
        // 由 NotificationEventListener 按“已存在”忽略。
        notification.setDedupeKey(event.dedupeKey());
        notificationMapper.insert(notification);
        // 提交后才推送：保证前端收到通知时已经能查到这条记录，不会点开就 404。
        pushAfterCommit(notification);
        return notification;
    }

    @Override
    public PageResult<Notification> listUserNotifications(Integer receiverId, String receiverRole,
                                                          List<String> categories,
                                                          List<String> types, int page, int pageSize) {
        List<String> allowedCategories = resolveCategories(receiverRole, categories);
        int safePage = PageUtils.normalizePage(page);
        int safeSize = PageUtils.normalizePageSize(pageSize);
        int offset = (safePage - 1) * safeSize;
        // 列表和总数用同一份 allowedCategories/types：过滤条件下推到 SQL，分页总数必须与列表口径一致，否则会翻出空白页。
        List<Notification> list = notificationMapper.selectByReceiver(
                receiverId, allowedCategories, types, offset, safeSize);
        int total = notificationMapper.countByReceiver(receiverId, allowedCategories, types);
        return new PageResult<>(list, total, safePage, safeSize);
    }

    @Override
    public PageResult<Notification> listAdminNotifications(String type, int page, int pageSize) {
        // 管理端只展示 system 分类（selectAll 的 SQL 固定 category = 'system'）：其它 type 提前拒绝，避免返回空列表被当成“没有数据”。
        if (type != null && !type.isBlank()
                && !NotificationCategory.SYSTEM.supports(type)) {
            throw new BusinessException("管理员通知记录仅支持系统通知类型");
        }
        int safePage = PageUtils.normalizePage(page);
        int safeSize = PageUtils.normalizePageSize(pageSize);
        int offset = (safePage - 1) * safeSize;
        List<Notification> list = notificationMapper.selectAll(type, offset, safeSize);
        int total = notificationMapper.countAll(type);
        return new PageResult<>(list, total, safePage, safeSize);
    }

    @Override
    public Notification getById(Long id) {
        // 只做单条读取，不在此判断归属；调用方必须拿 receiverId 比对，否则任意 id 都能读到别人的通知。
        return notificationMapper.selectById(id);
    }

    @Override
    public int getUnreadCount(Integer receiverId) {
        // 未登录或非法接收人返回 0：顶部红点接口在未登录态也会被调用，不能抛异常。
        return receiverId == null || receiverId < 0 ? 0 : notificationMapper.countUnread(receiverId);
    }

    @Override
    public Map<String, Integer> getUnreadCountsByCategory(Integer receiverId,
                                                          String receiverRole) {
        Set<String> allowedCategories =
                NotificationCategory.categoriesForRole(receiverRole);
        Map<String, Integer> result = new LinkedHashMap<>();
        // 键集合由角色决定并预置 0：前端分类 tab 需要固定键，SQL 只返回真正有未读的分类。
        for (NotificationCategory category : NotificationCategory.values()) {
            if (allowedCategories.contains(category.getCode())) {
                result.put(category.getCode(), 0);
            }
        }
        // 聚合只在允许的分类内进行：List.copyOf(result.keySet()) 已按角色过滤，商家不会统计到社区互动通知。
        for (NotificationCategoryCount count : notificationMapper
                .selectUnreadCountsByCategory(receiverId, List.copyOf(result.keySet()))) {
            result.put(count.getCategory(), count.getUnreadCount());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markRead(Long id, Integer receiverId) {
        Notification notification = notificationMapper.selectById(id);
        // 归属校验用 receiverId 比对：不是自己的通知返回 false（控制器转成“操作失败”），不暴露该 id 是否存在。
        if (notification == null || !notification.getReceiverId().equals(receiverId)) {
            return false;
        }
        // 条件更新带 user_id 与 deleted_at，影响行数为 0 表示已读过或已被删除：此时不再推送，避免重复回执把前端未读数刷乱。
        if (notificationMapper.markRead(id, receiverId) > 0) {
            // 推送携带最新未读数：前端不必再单独请求一次计数接口。
            sseService.pushEvent(receiverId, "notification-read", Map.of(
                    "id", id,
                    "unreadCount", notificationMapper.countUnread(receiverId)));
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Integer receiverId) {
        // 批量已读的 SQL 条件必须带 user_id = receiverId：否则一条 UPDATE 会把他人通知一起置为已读。
        notificationMapper.markAllRead(receiverId);
        // 全部已读后该用户的未读必然为 0，直接推常量而不必回查。
        sseService.pushEvent(receiverId, "notification-read-all", Map.of("unreadCount", 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 管理端删除只按 id 软删，不带 receiverId：归属由控制器 @RequireRole(ADMIN) 的入口保证。
        notificationMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteOwn(Long id, Integer receiverId) {
        // 删除条件同时带 id 与 user_id：影响行数为 0 表示通知不存在或不属于当前用户，统一返回 false，避免越权删他人通知。
        if (notificationMapper.deleteOwnById(id, receiverId) == 0) {
            return false;
        }
        // 推送删除事件：其它在线端同步移除该条，避免用户点开一条已删除的通知。
        sseService.pushEvent(receiverId, "notification-deleted", Map.of("id", id));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteAllRead(Integer receiverId) {
        // 只清已读：未读通知必须保留，用户不会因为一次“清空”丢掉还没处理的消息。
        int deleted = notificationMapper.deleteAllRead(receiverId);
        // 回查未读数：删除已读不影响未读，但前端仍需要一次完整的计数同步。
        sseService.pushEvent(receiverId, "notification-read-cleared", Map.of(
                "deleted", deleted,
                "unreadCount", notificationMapper.countUnread(receiverId)));
        return deleted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendAdminMessage(Integer senderId, String type, String title, String content,
                                 Integer receiverId, String targetRole) {
        String notificationType = defaultText(type, "system");
        String notificationCategory = NotificationCategory.SYSTEM.getCode();
        // 管理员公告固定 system 分类：type 必须落在 SYSTEM 白名单内，否则这批记录在管理端列表（SQL 固定 category = 'system'）里也查不到。
        if (!NotificationCategory.isValidPair(notificationCategory, notificationType)) {
            throw new BusinessException("管理员通知仅支持系统类通知");
        }
        if (targetRole != null && !targetRole.isBlank()) {
            // 按角色群发先取可用账号：selectMemberUserIds 只选 role in (user,business) 且 status = normal，禁用账号不接收公告。
            List<Integer> userIds = selectTargetUserIds(targetRole);
            if (userIds.isEmpty()) {
                return;
            }
            List<Notification> notifications = new ArrayList<>();
            for (Integer userId : userIds) {
                Notification notification = new Notification();
                notification.setReceiverId(userId);
                notification.setSenderId(senderId);
                notification.setCategory(notificationCategory);
                notification.setType(notificationType);
                notification.setLevel("info");
                notification.setTitle(title);
                notification.setContent(content);
                notifications.add(notification);
            }
            // 批量插入不写 dedupe_key：公告每次都是新通知，不参与 uk_notification_dedupe 去重。
            notificationMapper.insertBatch(notifications);
            // 逐条走 pushAfterCommit：推送发生在事务提交之后，推送失败不会影响已写入的通知。
            notifications.forEach(this::pushAfterCommit);
            return;
        }
        if (receiverId == null) {
            throw new BusinessException("请指定接收用户或接收角色");
        }
        // 单发也要校验接收人：必须是可用状态的普通用户或商家，避免把公告投给 admin 或不存在的账号。
        validateReceiver(receiverId);
        Notification notification = new Notification();
        notification.setReceiverId(receiverId);
        notification.setSenderId(senderId);
        notification.setCategory(notificationCategory);
        notification.setType(notificationType);
        notification.setLevel("info");
        notification.setTitle(title);
        notification.setContent(content);
        notificationMapper.insert(notification);
        pushAfterCommit(notification);
    }

    private String safeActionUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        // 只放行以单个 / 开头的站内路径：// 会被浏览器当作协议相对地址跳到外站，前端 router.push 前也做了同样判断。
        return value.startsWith("/") && !value.startsWith("//") ? value : null;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<String> resolveCategories(String receiverRole, List<String> requestedCategories) {
        Set<String> allowedCategories = NotificationCategory.categoriesForRole(receiverRole);
        // 角色没有任何可接收分类（如 admin）直接 403：这类账号本来就不在个人通知的接收范围内。
        if (allowedCategories.isEmpty()) {
            throw new BusinessException(403, "当前账号不接收个人通知");
        }
        // 不传分类表示查全部可读分类，而不是查全部通知：过滤范围始终由角色白名单决定。
        if (requestedCategories == null || requestedCategories.isEmpty()) {
            return new ArrayList<>(allowedCategories);
        }
        // 请求分类必须是白名单子集：客户端的 category 参数不能扩大可读范围。
        if (!allowedCategories.containsAll(requestedCategories)) {
            throw new BusinessException(403, "通知分类不适用于当前账号");
        }
        return requestedCategories;
    }

    private List<Integer> selectTargetUserIds(String targetRole) {
        // all 指会员群体（user + business）：管理端账号不在 selectMemberUserIds 的范围内。
        if ("all".equals(targetRole)) {
            return notificationMapper.selectMemberUserIds();
        }
        if (!AccountRole.USER.getCode().equals(targetRole)
                && !AccountRole.BUSINESS.getCode().equals(targetRole)) {
            throw new BusinessException("目标角色不合法");
        }
        return notificationMapper.selectUserIdsByRole(targetRole);
    }

    private void validateReceiver(Integer receiverId) {
        User receiver = userMapper.selectById(receiverId);
        String role = receiver == null ? null : receiver.getRole();
        if (!AccountStatus.NORMAL.getCode().equals(receiver == null ? null : receiver.getStatus())
                || (!AccountRole.USER.getCode().equals(role)
                && !AccountRole.BUSINESS.getCode().equals(role))) {
            throw new BusinessException("指定接收人必须是可用的普通用户或商家");
        }
    }

    private void pushAfterCommit(Notification notification) {
        // 事务同步激活时注册到 afterCommit：SSE 推送必须晚于提交，否则前端收到推送再回查会拿到尚未可见的记录。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            sseService.push(notification.getReceiverId(), notification);
                        }
                    });
            return;
        }
        sseService.push(notification.getReceiverId(), notification);
    }
}
