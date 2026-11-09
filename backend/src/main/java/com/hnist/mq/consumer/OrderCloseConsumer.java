package com.hnist.mq.consumer;

import com.hnist.mq.config.RabbitMqConfig;
import com.hnist.mq.message.OrderPayTimeoutMessage;
import com.hnist.service.OrderService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 订单关单消息消费者：消费支付超时消息，执行超时取消与库存释放。
 * 可靠性链路：消费异常按配置重试 3 次，耗尽后消息不重回队列而被丢弃，
 * 由 5 分钟一次的兜底轮询（PendingOrderExpiryJob）完成最终回收，保证最终一致。
 */
@Slf4j
@Component
public class OrderCloseConsumer {

    private final OrderService orderService;

    public OrderCloseConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 消费支付超时消息：cancelExpiredPendingOrder 内部用条件更新抢占 pending_payment 状态，
     * 用户已支付、消息重复投递、多实例并发消费都只会被静默跳过，不会重复释放库存或重复通知。
     */
    @RabbitListener(queues = RabbitMqConfig.ORDER_CLOSE_QUEUE)
    public void onOrderPayTimeout(OrderPayTimeoutMessage message) {
        orderService.cancelExpiredPendingOrder(message.orderId());
    }
}
