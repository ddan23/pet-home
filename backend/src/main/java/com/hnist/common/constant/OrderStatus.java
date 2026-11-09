package com.hnist.common.constant;

/** 订单状态，落 orders.status；商品单与预约单共用这套编码，差异由 order_type 区分。 */
public enum OrderStatus {

    /** 待支付：商品下单即写入，仅该状态可支付或取消，超时由定时任务回收并释放库存。 */
    PENDING_PAYMENT("pending_payment"),
    /** 已支付：支付回调条件更新成功时写入，商品单此后才允许发货。 */
    PAID("paid"),
    /** 已发货：商家发货后写入，买家可确认收货。 */
    SHIPPED("shipped"),
    /** 已完成：买家确认收货或预约核销后写入，终态。 */
    COMPLETED("completed"),
    /** 已取消：待支付商品单取消、超时关单或预约单取消后写入，终态。 */
    CANCELLED("cancelled"),
    /** 已确认：预约单提交即写入（跳过待支付），等待到店核销，可取消。 */
    CONFIRMED("confirmed");

    private final String code;

    OrderStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
