package com.hnist.common.constant;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** 站内通知分类，code 落 notifications.category；每类只接受白名单内的事件类型，写入与查询共用这份枚举。 */
public enum NotificationCategory {

    /** 系统通知：账号与平台公告，只接受 system 类型，普通用户与商家都能收。 */
    SYSTEM("system", Set.of("system")),
    /** 社区互动：关注、点赞、评论、审核结果与帖子上下线，仅普通用户接收。 */
    COMMUNITY("community", Set.of(
            "follow", "like", "comment", "moderation_pending",
            "moderation_passed", "moderation_blocked",
            "post_offline", "post_restored")),
    /** 商城交易：下单、支付、发货、完成、取消、退款与售后，用户与商家都可接收。 */
    MALL("mall", Set.of(
            "new_order", "paid", "shipped", "completed", "cancelled", "refund",
            "after_sale", "remind_shipment")),
    /** 服务预约：新预约、确认、完成、取消与开始提醒，用户与商家都可接收。 */
    SERVICE("service", Set.of(
            "new_booking", "confirmed", "completed", "cancelled", "start_reminder")),
    /** 健康提醒：疫苗/驱虫等到期与体重预警，仅普通用户；另放行 *_due 后缀类型。 */
    HEALTH("health", Set.of("health_reminder", "weight_alert"));

    private final String code;
    private final Set<String> allowedTypes;

    NotificationCategory(String code, Set<String> allowedTypes) {
        this.code = code;
        this.allowedTypes = allowedTypes;
    }

    public String getCode() {
        return code;
    }

    /** 事件类型白名单；health 额外放行 *_due 结尾的类型，用于按项目名动态生成的到期提醒。 */
    public boolean supports(String type) {
        if (this == HEALTH) {
            return allowedTypes.contains(type) || type.endsWith("_due");
        }
        return allowedTypes.contains(type);
    }

    /** 角色可见性：社区与健康只给普通用户，商家后台不出现；系统/商城/服务对两种角色开放。 */
    public boolean allowsRole(String role) {
        return switch (this) {
            case SYSTEM, MALL, SERVICE -> AccountRole.USER.getCode().equals(role)
                    || AccountRole.BUSINESS.getCode().equals(role);
            case COMMUNITY, HEALTH -> AccountRole.USER.getCode().equals(role);
        };
    }

    public static NotificationCategory fromCode(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /** 写入前校验分类与类型是否配对，挡掉前端拼出的不存在事件组合。 */
    public static boolean isValidPair(String category, String type) {
        NotificationCategory item = fromCode(category);
        return item != null && item.supports(type);
    }

    /** 按角色反查可收分类，查询通知时用它拼 IN 条件，越权分类在 SQL 层就被过滤。 */
    public static Set<String> categoriesForRole(String role) {
        return Arrays.stream(values())
                .filter(item -> item.allowsRole(role))
                .map(NotificationCategory::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }
}
