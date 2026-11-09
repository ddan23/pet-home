package com.hnist.mapper;

import com.hnist.entity.AppointmentSlot;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 具体预约时段数据访问。 */
public interface AppointmentSlotMapper {

    List<AppointmentSlot> selectPublicByServiceId(
            @Param("serviceId") Integer serviceId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    List<AppointmentSlot> selectByServiceIdAndShopId(
            @Param("serviceId") Integer serviceId,
            @Param("shopId") Integer shopId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    AppointmentSlot selectById(@Param("id") Integer id);

    List<AppointmentSlot> selectByIds(@Param("ids") List<Integer> ids);

    List<AppointmentSlot> selectByOrderIds(@Param("orderIds") List<Integer> orderIds);

    int countByServiceIdAndDateAndStartTime(
            @Param("serviceId") Integer serviceId,
            @Param("slotDate") LocalDate slotDate,
            @Param("startTime") LocalTime startTime);

    int countByServiceIdAndShopIdAndDate(
            @Param("serviceId") Integer serviceId,
            @Param("shopId") Integer shopId,
            @Param("slotDate") LocalDate slotDate);

    int updateDayStatus(@Param("serviceId") Integer serviceId,
                        @Param("shopId") Integer shopId,
                        @Param("slotDate") LocalDate slotDate,
                        @Param("dayStatus") String dayStatus);

    // 只删无订单引用的未约时段（FK 防止误删被取消/完成订单引用的历史凭证行）
    int deleteByServiceIdAndShopIdAndDate(
            @Param("serviceId") Integer serviceId,
            @Param("shopId") Integer shopId,
            @Param("slotDate") LocalDate slotDate);

    // 隐藏被订单引用且已无人预约的时段（day_status 置 CLOSED），生成时段命中同一 start_time 时恢复
    int hideReferencedUnbookedSlots(
            @Param("serviceId") Integer serviceId,
            @Param("shopId") Integer shopId,
            @Param("slotDate") LocalDate slotDate);

    /** INSERT IGNORE：同一时段重复生成时命中唯一键被忽略，保证排班幂等。 */
    int insert(AppointmentSlot slot);

    // 只刷新未被预约（booked_count = 0）且未 CLOSED 的时段，已预约时段的容量/时间不可改
    int refreshUnbookedTemplate(AppointmentSlot slot);

    // 条件抢占名额：仅 status = 'OPEN' 且 booked_count < capacity 时 +1，返回 0 表示已满或已关闭
    int tryReserve(@Param("id") Integer id);

    // 释放名额：GREATEST 防止 booked_count 减到负数
    int release(@Param("id") Integer id);

    // 调容量：排除 EXPIRED，且新容量不得小于已约数（booked_count <= capacity 才生效）
    int updateCapacity(@Param("id") Integer id,
                       @Param("shopId") Integer shopId,
                       @Param("capacity") Integer capacity);

    // 关闭时段：仅 status = 'OPEN' 且 booked_count = 0 可关，有人预约的时段不能关
    int close(@Param("id") Integer id,
              @Param("shopId") Integer shopId);

    // 重开时段：仅 status = 'CLOSED' 可重开
    int open(@Param("id") Integer id,
             @Param("shopId") Integer shopId);
}
