package com.hnist.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** orders 表行映射：商品单与预约单共用，orderType 区分 product、booking，明细以下单时的 JSON 快照保存。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据库主键，系统内部使用 */
    private Integer id;
    /** 业务订单号，唯一索引 uk_order_no，面向用户展示 */
    private String orderNo;
    private Integer userId;
    private Integer shopId;
    /** 取值 product（商品单）、booking（预约单） */
    private String orderType;
    /** 订单总金额，decimal(10,2) */
    private BigDecimal totalAmount;
    /** 取值 pending_payment、paid、shipped、completed、cancelled、confirmed（OrderStatus） */
    private String status;
    /** 预约时间 */
    private LocalDateTime appointmentTime;
    /** 多时段预约的最后结束时间 */
    private LocalDateTime appointmentEndTime;
    /** 预约时长文本快照，varchar(20) */
    private String appointmentDuration;
    /** 预约时宠物名称快照 */
    private String appointmentPetName;
    /** 收货地址 JSON 快照 */
    private String shippingAddress;
    /** 订单明细 JSON 快照，下单后不随商品改价变化 */
    private String items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 非DB字段，关联 shops.name */
    private String shopName;
    /** 非DB字段，关联 shops.address，预约单到店地址 */
    private String shopAddress;
    /** 非DB字段，关联 users.nickname */
    private String userName;
    /** 非DB字段，装配后的关联时段列表 */
    private List<AppointmentSlot> appointmentSlots;
}
