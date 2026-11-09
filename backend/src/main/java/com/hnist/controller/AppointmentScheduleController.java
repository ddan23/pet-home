package com.hnist.controller;

import com.hnist.common.constant.AccountRole;

import com.hnist.annotation.CurrentUser;
import com.hnist.annotation.RequireRole;
import com.hnist.common.result.Result;
import com.hnist.dto.SaveAppointmentScheduleRequest;
import com.hnist.dto.UpdateAppointmentSlotRequest;
import com.hnist.entity.AppointmentSchedule;
import com.hnist.entity.AppointmentSlot;
import com.hnist.entity.User;
import com.hnist.service.AppointmentScheduleService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** 预约排班表、可约时段与商家时段管理控制器。 */
@RestController
@RequestMapping({"/api", "/api/v1"})
public class AppointmentScheduleController {

    private final AppointmentScheduleService appointmentScheduleService;

    public AppointmentScheduleController(
            AppointmentScheduleService appointmentScheduleService) {
        this.appointmentScheduleService = appointmentScheduleService;
    }

    @GetMapping("/services/{serviceId}/booking-slots")
    public Result<List<AppointmentSlot>> listBookingSlots(
            @PathVariable Integer serviceId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(appointmentScheduleService.listPublicSlots(serviceId, from, to));
    }

    /** 商家端排班接口均以登录态 userId 参与归属判断：outletId 只能指向本人门店，越权由 service 拒绝。 */
    /** 读该服务的每日排班时间段模板：未设置过时 data 为 null，前端据此显示空表单。 */
    @GetMapping("/merchant/services/{serviceId}/appointment-schedule")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<AppointmentSchedule> getSchedule(
            @PathVariable Integer serviceId,
            @RequestParam(name = "outletId", required = false) Integer outletId,
            @CurrentUser User user) {
        return Result.ok(appointmentScheduleService.getMerchantSchedule(
                user.getId(), serviceId, outletId));
    }

    @PostMapping("/merchant/services/{serviceId}/appointment-schedule")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<AppointmentSchedule> saveSchedule(
            @PathVariable Integer serviceId,
            @RequestParam(name = "outletId", required = false) Integer outletId,
            @Valid @RequestBody SaveAppointmentScheduleRequest request,
            @CurrentUser User user) {
        return Result.ok("时间段已保存，未来6天已按新时间段重建（已约时段保留）",
                appointmentScheduleService.saveSchedule(
                        user.getId(), serviceId, outletId, request));
    }

    @GetMapping("/merchant/services/{serviceId}/appointment-slots")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<List<AppointmentSlot>> listMerchantSlots(
            @PathVariable Integer serviceId,
            @RequestParam(name = "outletId", required = false) Integer outletId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @CurrentUser User user) {
        return Result.ok(appointmentScheduleService.listMerchantSlots(
                user.getId(), serviceId, outletId, from, to));
    }

    @PutMapping("/merchant/appointment-slots/{id}/capacity")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<AppointmentSlot> updateSlotCapacity(
            @PathVariable Integer id,
            @RequestParam(name = "outletId", required = false) Integer outletId,
            @Valid @RequestBody UpdateAppointmentSlotRequest request,
            @CurrentUser User user) {
        return Result.ok("时段容量已更新",
                appointmentScheduleService.updateSlotCapacity(
                        user.getId(), id, outletId, request.getCapacity()));
    }

    @PutMapping("/merchant/appointment-slots/{id}/close")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Void> closeSlot(
            @PathVariable Integer id,
            @RequestParam(name = "outletId", required = false) Integer outletId,
            @CurrentUser User user) {
        appointmentScheduleService.closeSlot(user.getId(), id, outletId);
        return Result.ok("时段已关闭", null);
    }

    @PutMapping("/merchant/appointment-slots/{id}/open")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Void> openSlot(
            @PathVariable Integer id,
            @RequestParam(name = "outletId", required = false) Integer outletId,
            @CurrentUser User user) {
        appointmentScheduleService.openSlot(user.getId(), id, outletId);
        return Result.ok("时段已开启", null);
    }

    @PutMapping("/merchant/services/{serviceId}/appointment-slots/day-status")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Void> setDayStatus(
            @PathVariable Integer serviceId,
            @RequestParam(name = "outletId", required = false) Integer outletId,
            @RequestParam(name = "date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "status") String status,
            @RequestParam(name = "reason", required = false) String reason,
            @CurrentUser User user) {
        // 休业会取消该天全部未来已确认预约并按原因逐单通知用户；service 返回取消的订单数供提示组装。
        int cancelled = appointmentScheduleService.setDayStatus(
                user.getId(), serviceId, outletId, date, status, reason);
        String message = "OPEN".equalsIgnoreCase(status)
                ? "该天已恢复营业"
                : (cancelled > 0
                    ? "该天已休业，已取消 " + cancelled + " 条预约并通知用户"
                    : "该天已休业");
        return Result.ok(message, null);
    }
}
