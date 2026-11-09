package com.hnist.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

/** 商家每日排班时间段保存请求：一服务一份模板，时段切分粒度由服务的时长与间隔决定，不在请求里传。 */
@Data
public class SaveAppointmentScheduleRequest {

    /** 排班可配窗口天数：今天起第 1~6 天，与用户端 7 天可约窗口对齐 */
    public static final int BOOKABLE_DAYS = 6;

    @NotNull(message = "开始时间不能为空")
    private LocalTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalTime endTime;

    /** 午休开始时间：可空，空表示无休息区间 */
    private LocalTime breakStart;

    /** 午休结束时间：可空，空表示无休息区间 */
    private LocalTime breakEnd;

    /** 服务间隔分钟数：两段服务之间的缓冲，为空按 0 落库 */
    @Min(value = 0, message = "服务间隔不能小于0分钟")
    @Max(value = 120, message = "服务间隔不能超过120分钟")
    private Integer intervalMinutes;

    @NotNull(message = "时段容量不能为空")
    @Min(value = 1, message = "每个时段至少容纳1只宠物")
    @Max(value = 100, message = "每个时段最多容纳100只宠物")
    private Integer capacityPerSlot;
}
