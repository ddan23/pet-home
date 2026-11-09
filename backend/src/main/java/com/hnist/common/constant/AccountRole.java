package com.hnist.common.constant;

/** 账号角色，落 users.role；注册只允许 user 与 business，admin 仅由初始化数据产生。 */
public enum AccountRole {

    /** 普通用户：注册默认角色（库表 DEFAULT 'user'），C 端 @RequireRole 的准入值。 */
    USER("user"),
    /** 商家：注册时自选，可创建店铺、访问商家后台与客服会话。 */
    BUSINESS("business"),
    /** 管理员：后台审核与知识库管理的准入角色，注册接口无法创建。 */
    ADMIN("admin");

    // 注解参数只接受编译期常量，故额外暴露 code 常量；改枚举 code 时必须同步这两处。
    public static final String USER_CODE = "user";
    public static final String BUSINESS_CODE = "business";
    public static final String ADMIN_CODE = "admin";

    private final String code;

    AccountRole(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
