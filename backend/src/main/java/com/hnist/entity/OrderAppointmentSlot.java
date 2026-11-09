package com.hnist.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** order_appointment_slots 表行映射：一笔预约订单与所占时段的对应关系。 */
@Data
public class OrderAppointmentSlot implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer orderId;
    private Integer slotId;
    private Integer petId;
    private LocalDateTime createdAt;
}
