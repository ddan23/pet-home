package com.hnist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/** 自定义健康提醒新增 / 修改入参（POST/PUT /api/ai/health/reminders）：只能挂在当前用户名下的宠物上。 */
@Data
public class SaveHealthReminderRequest {

    @NotNull(message = "请选择宠物")
    private Integer petId;

    @NotBlank(message = "请填写提醒标题")
    @Size(max = 100, message = "提醒标题不能超过100个字符")
    private String title;

    /** 为空表示不定时的长期提醒：不进到期扫描，创建时直接发一次通知；非空必须晚于当前时间。 */
    private LocalDateTime remindAt;

    @Size(max = 500, message = "提醒说明不能超过500个字符")
    private String note;
}
