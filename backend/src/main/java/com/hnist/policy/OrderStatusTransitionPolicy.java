package com.hnist.policy;

import com.hnist.common.constant.OrderStatus;

/**
 * 订单与预约状态机的唯一白名单入口：各接口在此统一判定，防止不同入口写出互相矛盾的状态集。
 * 这里只做前置判断，真正的并发保护由调用方的条件更新（updateStatusIfCurrent）完成。
 */
public final class OrderStatusTransitionPolicy {

    private OrderStatusTransitionPolicy() {
    }

    /** 只有 pending_payment 可支付：已进入 paid/cancelled 的订单重复支付会重复发货或重复回补库存。 */
    public static boolean isPayable(String status) {
        return OrderStatus.PENDING_PAYMENT.getCode().equals(status);
    }

    /** 商品订单只有 pending_payment 可由用户取消：支付完成后没有退款流程，只能继续履约。 */
    public static boolean isProductCancellable(String status) {
        return OrderStatus.PENDING_PAYMENT.getCode().equals(status);
    }

    /** 预约只有 confirmed 可被取消或改写终态：completed/cancelled 已是终态，重复操作会二次归还时段名额。 */
    public static boolean isBookingCancellable(String status) {
        return OrderStatus.CONFIRMED.getCode().equals(status);
    }

    /** 只有 paid 可发货：未付款先出库会让商家在用户弃单时白损失商品。 */
    public static boolean isShippable(String status) {
        return OrderStatus.PAID.getCode().equals(status);
    }

    /** 只有 shipped 可确认收货：用户无法跳过发货直接把订单置为 completed。 */
    public static boolean isReceivable(String status) {
        return OrderStatus.SHIPPED.getCode().equals(status);
    }

    /** 商家处理预约只能写终态 completed/cancelled：其余状态值不由此接口维护，放行会制造游离于状态机之外的订单。 */
    public static boolean isAppointmentResultStatus(String status) {
        return OrderStatus.COMPLETED.getCode().equals(status)
                || OrderStatus.CANCELLED.getCode().equals(status);
    }
}
