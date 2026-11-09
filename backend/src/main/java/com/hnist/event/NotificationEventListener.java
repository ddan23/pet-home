package com.hnist.event;

import com.hnist.mq.config.RabbitMqConfig;

import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 在业务事务提交后把通知事件投递到 RabbitMQ 扇出交换机。
 * AFTER_COMMIT：业务数据已落库才发消息，回滚的事务不会产生幽灵通知；
 * fallbackExecution = true：发布方没有事务时也执行，事件不丢。
 * 投递在发布线程同步执行（毫秒级发送），失败只记日志：通知属旁路，不影响已提交的业务结果。
 */
@Slf4j
@Component
public class NotificationEventListener {

    private final RabbitTemplate rabbitTemplate;

    public NotificationEventListener(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 事务提交后发消息到通知扇出交换机：落库与推送由通知消费者异步完成，
     * 发布方与通知实现彻底解耦，新增投递渠道无需改动任何业务代码。
     * 发送失败记 warn 后放弃——与原「通知旁路失败不影响业务」语义一致；
     * dedupeKey 唯一索引继续在消费端收敛重复投递。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(NotificationEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.NOTIFICATION_FANOUT_EXCHANGE, "", event);
        } catch (RuntimeException exception) {
            log.warn("通知消息发送失败，接收人：{}，类型：{}",
                    event.receiverId(), event.type(), exception);
        }
    }
}
