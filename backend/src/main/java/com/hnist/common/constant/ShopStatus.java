package com.hnist.common.constant;

/** 店铺状态，落 shops.status；新建店铺先待审核，只有 open 能承接交易。 */
public enum ShopStatus {

    /** 营业中：审核通过或关店后重新开业时写入，所有对外入口的共同前置条件。 */
    OPEN("open"),
    /** 已关闭：商家歇业时写入，只挡新交易，历史订单与预约仍保留在店内。 */
    CLOSED("closed"),
    /** 审核驳回：资料不合规时写入，商家修改资料后自动回到待审核。 */
    REJECTED("rejected"),
    /** 待审核：新建或被驳回后重新提交时写入，不能直接改成 open 绕过审核。 */
    PENDING_AUDIT("pending_audit");

    private final String code;

    ShopStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
