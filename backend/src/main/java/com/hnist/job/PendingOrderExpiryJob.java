package com.hnist.job;

import com.hnist.service.OrderService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 待支付商品订单超时取消的兜底任务：主力链路已改为 RabbitMQ TTL + 死信队列延迟关单，
 * 本任务降频为每 5 分钟执行一次，仅负责覆盖两类残余场景——MQ 不可用导致消息未投递、
 * 消费重试耗尽后消息被丢弃。兜底口径与延迟消息一致：回收 createdAt 超过 10 分钟
 * 仍为 pending_payment 的商品订单并回补库存。
 * 幂等性：取消走条件更新（pending_payment → cancelled），影响行数为 0 说明用户刚支付
 * 或已被 MQ 消费者处理，跳过；只有抢到状态更新的那一次回补库存，与 MQ 消费者并发也不会多放库存。
 */
@Slf4j
@Component
public class PendingOrderExpiryJob {

    private final OrderService orderService;

    public PendingOrderExpiryJob(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 异常只记日志：整批回收在同一事务内，失败则整体回滚，下一个周期重试即可；抛出反而中断调度线程。 */
    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Shanghai")
    public void cancelExpiredPendingOrders() {
        try {
            int cancelledCount = orderService.cancelExpiredPendingOrders();
            if (cancelledCount > 0) {
                log.info("兜底轮询已自动取消{}笔超时未支付订单", cancelledCount);
            }
        } catch (RuntimeException exception) {
            log.error("兜底轮询处理超时未支付订单失败", exception);
        }
    }
}
