package com.hnist.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** appointment_schedules 表行映射：每服务一份每日时间段模板，保存后窗口内每天默认按模板排班且全部开启。 */
@Data
public class AppointmentSchedule implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer shopId;
    private Integer serviceId;
    private LocalTime startTime;
    private LocalTime endTime;
    /** 午休开始时间：空表示无休息区间 */
    private LocalTime breakStart;
    /** 午休结束时间：空表示无休息区间 */
    private LocalTime breakEnd;
    /** 服务间隔分钟数：两段服务之间的缓冲，时段步长 = 服务时长 + 服务间隔 */
    private Integer intervalMinutes;
    /** 单个时段可预约宠物数 */
    private Integer capacityPerSlot;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
