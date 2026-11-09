package com.hnist.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 单个预约时段容量修改请求。 */
@Data
public class UpdateAppointmentSlotRequest {

    // 不能低于该时段已预约数，否则后续条件更新永远不成立，这个时段再也约不上。
    @NotNull(message = "时段容量不能为空")
    @Min(value = 1, message = "时段容量不能小于1")
    @Max(value = 100, message = "时段容量不能超过100")
    private Integer capacity;
}
