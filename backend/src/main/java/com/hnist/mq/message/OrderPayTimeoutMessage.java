package com.hnist.mq.message;

/**
 * 订单支付超时消息体：下单事务提交后投递进 TTL 延迟队列，
 * 到期由 RabbitMQ 经死信交换机转发到关单队列，消费者按订单 id 执行超时取消。
 *
 * @param orderId 商品订单 id
 */
public record OrderPayTimeoutMessage(Integer orderId) {
}
