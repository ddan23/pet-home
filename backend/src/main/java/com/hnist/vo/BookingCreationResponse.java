package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 多时段预约下单结果：一张订单占用 1~8 个连续时段，金额与时段数在服务端同时确定。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingCreationResponse {

    private Integer orderId;

    private String orderNo;

    /** 本单占用的时段数，同时决定 totalAmount，前端据此展示“N 个时段”。 */
    private Integer slotCount;

    /** 订单总额（元）：服务单价 × slotCount，不接受前端传入金额。 */
    private BigDecimal totalAmount;
}
