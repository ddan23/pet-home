package com.hnist.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** appointment_slots 表行映射：按服务拆分的可预约时段，名额用容量与已约数表达。 */
@Data
public class AppointmentSlot implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer shopId;
    private Integer serviceId;
    private LocalDate slotDate;
    /** 天级状态：OPEN 营业、CLOSED 全天休业 */
    private String dayStatus;
    private LocalTime startTime;
    private LocalTime endTime;
    /** 可预约宠物数 */
    private Integer capacity;
    /** 已预约数 */
    private Integer bookedCount;
    /** 时段状态：OPEN、FULL、CLOSED、EXPIRED */
    private String status;
    /** 乐观锁版本号，扣减名额时自增 */
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 非DB字段，capacity - booked_count 的计算列 */
    private Integer remaining;
    /** 非DB字段，关联 products.name */
    private String serviceName;
    /** 非DB字段，按订单查询时由 order_appointment_slots 带出 */
    private Integer orderId;
}
