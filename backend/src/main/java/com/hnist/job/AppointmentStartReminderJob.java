package com.hnist.job;

import com.hnist.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 服务预约开始前提醒任务：每分钟（cron "0 * * * * *"，东八区）扫描 2 小时内即将开始的 confirmed 预约并推送提醒。
 * 幂等性：重复执行由通知 dedupeKey（booking-start-user/booking-start-merchant:{orderId}）的唯一索引去重，
 * 窗口期内反复扫描每个预约只各留一条用户/商家提醒；多实例同时部署时同样靠该唯一索引收敛。
 */
@Slf4j
@Component
public class AppointmentStartReminderJob {

    private final OrderService orderService;

    public AppointmentStartReminderJob(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 异常只记日志不外抛：单轮失败下一分钟自然重试，抛出反而会中断调度线程的后续触发。 */
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Shanghai")
    public void remindUpcomingAppointments() {
        try {
            orderService.remindUpcomingAppointments();
        } catch (RuntimeException exception) {
            log.error("预约开始前提醒任务执行失败", exception);
        }
    }
}
