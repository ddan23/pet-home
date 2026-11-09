package com.hnist.service;

import com.hnist.vo.BookingCreationResponse;
import com.hnist.dto.SaveAppointmentScheduleRequest;
import com.hnist.entity.AppointmentSchedule;
import com.hnist.entity.AppointmentSlot;

import java.time.LocalDate;
import java.util.List;

/** 预约排班模板维护、时段容量管理与预约下单契约。 */
public interface AppointmentScheduleService {

    /** from/to 可空，窗口强制收敛到今天起 7 天：只返回门店营业且尚未结束的时段。 */
    List<AppointmentSlot> listPublicSlots(Integer serviceId, LocalDate from, LocalDate to);

    /** 读该服务的每日时间段模板：未设置过返回 null，由接口层按「未设置」提示。 */
    AppointmentSchedule getMerchantSchedule(
            Integer ownerId, Integer serviceId, Integer shopId);

    /**
     * 保存每日时间段模板并重建可约窗口内的时段：窗口内每天默认按模板排班且全部开启；
     * 重建只删 booked_count = 0 的时段，已预约时段原样保留，旧订单引用不受影响；
     * 休业天的状态在重建后保持，不会被洗成可约。
     */
    AppointmentSchedule saveSchedule(
            Integer ownerId, Integer serviceId, Integer shopId,
            SaveAppointmentScheduleRequest request);

    /** 商家时段列表（含已约人数）：只读不生成，窗口强制收敛到今天起 7 天。 */
    List<AppointmentSlot> listMerchantSlots(
            Integer ownerId, Integer serviceId, Integer shopId,
            LocalDate from, LocalDate to);

    /** 容量不得低于 booked_count，已结束的时段不允许修改。 */
    AppointmentSlot updateSlotCapacity(
            Integer ownerId, Integer slotId, Integer shopId, Integer capacity);

    /** 仅未开始且 booked_count = 0 的时段可以关闭，否则已确认的预约会失去承载时段。 */
    void closeSlot(Integer ownerId, Integer slotId, Integer shopId);

    /** 只对 CLOSED 时段生效：已开始的时段不能开启，状态被并发改写时抛业务异常，需刷新重试。 */
    void openSlot(Integer ownerId, Integer slotId, Integer shopId);

    /** 天级营业开关：休业时取消该天全部未来已确认预约并按原因通知用户；返回取消的订单数供提示组装。 */
    int setDayStatus(Integer ownerId, Integer serviceId, Integer shopId,
                     LocalDate slotDate, String dayStatus, String reason);

    /** 在同一事务内占用全部 slotIds 的容量并创建预约单，任一时段占用失败则整单回滚，不会部分生效。 */
    BookingCreationResponse createBooking(
            Integer userId, Integer serviceId, Integer petId,
            List<Integer> slotIds, String note);
}
