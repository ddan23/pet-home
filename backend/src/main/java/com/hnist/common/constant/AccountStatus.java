package com.hnist.common.constant;

/** 账号状态，落 users.status；disabled 的账号无法登录，也收不到通知与私信。 */
public enum AccountStatus {

    /** 正常：注册默认值；店铺审核通过或恢复营业时也会把商家重置为该值。 */
    NORMAL("normal"),
    /** 禁用：管理端停用时写入；登录、通知投递与私信入口都会按该值拒绝。 */
    DISABLED("disabled");

    private final String code;

    AccountStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
