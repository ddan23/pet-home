package com.hnist.policy;

import com.hnist.common.exception.BusinessException;
import com.hnist.dto.SaveAppointmentScheduleRequest;

import java.time.LocalTime;

/** 预约排班模板校验：所有取值范围以这里为准，非法模板一旦入库会在时段重建时切出坏时段污染可售列表。 */
public final class AppointmentSchedulePolicy {

    // 排班结束时间硬上限：时段按服务时长从开始时间向后切分，结束越界到次日会让切分循环跨天。
    private static final LocalTime LATEST_SLOT_END = LocalTime.of(23, 59);

    private AppointmentSchedulePolicy() {
    }

    /**
     * 每日时间段模板的取值校验：营业区间与午休区间必须合法且午休不跨界，
     * 容量范围决定重建时段的写入规模，非法值会在保存动作里批量写坏数据。
     */
    public static void validateTemplate(SaveAppointmentScheduleRequest request) {
        LocalTime startTime = request.getStartTime();
        LocalTime endTime = request.getEndTime();
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new BusinessException("开始时间必须早于结束时间");
        }
        if (endTime.isAfter(LATEST_SLOT_END)) {
            throw new BusinessException("结束时间不能晚于当天23:59");
        }
        LocalTime breakStart = request.getBreakStart();
        LocalTime breakEnd = request.getBreakEnd();
        if (breakStart == null && breakEnd == null) {
            validateInterval(request.getIntervalMinutes());
            return;
        }
        if (breakStart == null || breakEnd == null) {
            throw new BusinessException("午休开始与结束时间必须同时填写");
        }
        if (!breakStart.isBefore(breakEnd)) {
            throw new BusinessException("午休开始时间必须早于结束时间");
        }
        if (breakStart.isBefore(startTime) || breakEnd.isAfter(endTime)) {
            throw new BusinessException("午休区间必须完全落在营业时间之内");
        }
        validateInterval(request.getIntervalMinutes());
        Integer capacity = request.getCapacityPerSlot();
        if (capacity == null || capacity < 1 || capacity > 100) {
            throw new BusinessException("时段容量必须在1到100之间");
        }
    }

    /** 服务间隔只做缓冲不承载服务：负数会让步长小于时长造成时段重叠，上限防止单天被切空。 */
    private static void validateInterval(Integer intervalMinutes) {
        if (intervalMinutes == null || intervalMinutes < 0 || intervalMinutes > 120) {
            throw new BusinessException("服务间隔必须在0到120分钟之间");
        }
    }
}
