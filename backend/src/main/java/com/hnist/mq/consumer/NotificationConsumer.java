package com.hnist.mq.consumer;

import com.hnist.event.NotificationEvent;
import com.hnist.mq.config.RabbitMqConfig;
import com.hnist.service.NotificationService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 通知消息消费者：绑定通知落库队列，把扇出交换机广播的通知事件写入数据库并推送在线用户。
 * 扩展方式：后续新增邮件、短信渠道时，只需为新队列绑定同一扇出交换机并追加消费者，本类无需改动。
 * 幂等性：dedupeKey 唯一索引（uk_notification_dedupe）收敛重复投递，同一事件重复消费只落一条记录。
 */
@Slf4j
@Component
public class NotificationConsumer {

    private final NotificationService notificationService;

    public NotificationConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 消费通知消息：异常一律吞掉——通知是旁路链路，把失败抛回去只会让消息在重试与丢弃间空转；
     * DuplicateKeyException 按「已存在」处理属预期路径，其余运行时异常记 warn 后放弃，不影响业务主流程。
     */
    @RabbitListener(queues = RabbitMqConfig.NOTIFICATION_WRITE_QUEUE)
    public void onNotification(NotificationEvent event) {
        try {
            notificationService.create(event);
        } catch (DuplicateKeyException exception) {
            log.debug("通知已存在，跳过重复写入：{}", event.dedupeKey());
        } catch (RuntimeException exception) {
            log.warn("通知写入失败，接收人：{}，类型：{}",
                    event.receiverId(), event.type(), exception);
        }
    }
}
