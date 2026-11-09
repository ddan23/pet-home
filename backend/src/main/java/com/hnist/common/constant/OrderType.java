package com.hnist.common.constant;

/** 订单类型，落 orders.order_type；决定状态流转入口：product 走支付发货，booking 走时段核销。 */
public enum OrderType {

    /** 商品订单：按商品库存扣减，状态从 pending_payment 开始。 */
    PRODUCT("product"),
    /** 预约订单：按时段名额占用，提交即 confirmed，取消时释放名额。 */
    BOOKING("booking");

    private final String code;

    OrderType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
