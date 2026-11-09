package com.hnist.mapper;

import com.hnist.entity.OrderAppointmentSlot;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 预约订单时段关联数据访问。 */
public interface OrderAppointmentSlotMapper {

    int batchInsert(@Param("items") List<OrderAppointmentSlot> items);

    /** 条件释放：仅 booked_count > 0 的时段回退 1 个名额并用 GREATEST 防负数，同时限定 shop_id 隔离。 */
    int releaseCapacityByOrderId(@Param("orderId") Integer orderId,
                                 @Param("shopId") Integer shopId);

    /** 只统计 status = 'confirmed' 的 booking 订单且预约结束时间在未来的记录，按 DISTINCT orders.id 去重。 */
    int countFutureBookingsByPet(@Param("petId") Integer petId,
                                 @Param("userId") Integer userId);
}
