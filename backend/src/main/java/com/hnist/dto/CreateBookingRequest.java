package com.hnist.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 服务预约下单入参（POST /api/orders/booking）：多个时段合并成一笔预约单，金额由服务单价乘时段数在服务端算出。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequest {

    @NotNull(message = "服务ID不能为空")
    private Integer serviceId;

    @NotNull(message = "宠物ID不能为空")
    private Integer petId;

    // 时段 id 来自商家排班生成的 appointment_slots：逐个校验归属服务、未开始、未约满，任一段占用失败整单回滚。
    @NotEmpty(message = "请至少选择一个预约时段")
    @Size(max = 8, message = "一次最多选择8个预约时段")
    private List<@NotNull(message = "预约时段ID不能为空") Integer> slotIds;

    @Size(max = 500, message = "预约备注不能超过500个字符")
    private String note;
}
