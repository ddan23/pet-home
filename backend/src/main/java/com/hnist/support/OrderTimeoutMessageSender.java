package com.hnist.support;

import com.hnist.mq.config.RabbitMqConfig;
import com.hnist.mq.message.OrderPayTimeoutMessage;

import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 订单支付超时消息发送器：把订单 id 投递进 TTL 延迟队列，到期经死信交换机触发关单。
 * 事务感知：调用方处于事务中时注册 afterCommit 回调，事务提交成功才发消息——
 * 回滚的订单不会留下幽灵超时消息；无事务上下文时立即发送，事件不丢。
 * 发送失败只记 error 日志：兜底轮询（PendingOrderExpiryJob）会在超时后回收订单，保证最终一致。
 */
@Slf4j
@Component
public class OrderTimeoutMessageSender {

    private final RabbitTemplate rabbitTemplate;

    public OrderTimeoutMessageSender(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 在事务提交后投递支付超时消息；必须在实际事务方法内调用，否则注册不到同步回调。
     *
     * @param orderId 已落库的商品订单 id
     */
    public void sendAfterCommit(Integer orderId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSend(orderId);
                }
            });
        } else {
            doSend(orderId);
        }
    }

    /** 实际投递：异常在此收敛——afterCommit 阶段抛出无法回滚已提交的订单，只会打断调用链。 */
    private void doSend(Integer orderId) {
        try {
            rabbitTemplate.convertAndSend(RabbitMqConfig.ORDER_PAY_TIMEOUT_EXCHANGE,
                    RabbitMqConfig.ORDER_PAY_TIMEOUT_ROUTING_KEY,
                    new OrderPayTimeoutMessage(orderId));
        } catch (RuntimeException exception) {
            log.error("订单支付超时消息发送失败，交由兜底轮询回收，orderId={}", orderId, exception);
        }
    }
}
