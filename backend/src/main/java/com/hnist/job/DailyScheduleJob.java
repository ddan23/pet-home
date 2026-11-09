package com.hnist.job;

import com.hnist.entity.AppointmentSchedule;
import com.hnist.mapper.AppointmentScheduleMapper;
import com.hnist.service.impl.AppointmentScheduleServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 每日排班兜底任务：每天 01:00（cron "0 0 1 * * *"，东八区）为所有已配置时间段模板的服务
 * 补齐可约窗口内「还没有时段」的日期，新的一天滚动进入窗口时由此自动排班且默认开启。
 * 幂等性：保存模板是时段的唯一权威来源且实时重建，任务只处理完全无时段的天，
 * 重复执行、多实例同时执行都不会覆盖已生成的时段或冲掉休业状态。
 * 失败影响面：单个服务失败只 log.warn 并继续其余服务，下一轮任务兜底。
 */
@Slf4j
@Component
public class DailyScheduleJob {

    private final AppointmentScheduleMapper scheduleMapper;
    private final AppointmentScheduleServiceImpl scheduleService;

    public DailyScheduleJob(AppointmentScheduleMapper scheduleMapper,
                            AppointmentScheduleServiceImpl scheduleService) {
        this.scheduleMapper = scheduleMapper;
        this.scheduleService = scheduleService;
    }

    /** 单服务失败不中断整轮：跳过该服务继续其余，凌晨失败由白天的商家保存动作兜底补齐。 */
    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Shanghai")
    public void ensureDailySchedules() {
        List<AppointmentSchedule> templates = scheduleMapper.selectAllTemplates();
        for (AppointmentSchedule template : templates) {
            try {
                int generated = scheduleService.ensureSlotsForJob(
                        template.getShopId(), template.getServiceId());
                if (generated > 0) {
                    log.info("排班时段补齐完成，店铺：{}，服务：{}，新增时段：{}",
                            template.getShopId(), template.getServiceId(), generated);
                }
            } catch (RuntimeException exception) {
                log.warn("排班时段生成失败，店铺：{}，服务：{}",
                        template.getShopId(), template.getServiceId(), exception);
            }
        }
    }
}
