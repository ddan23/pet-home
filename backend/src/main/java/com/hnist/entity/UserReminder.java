package com.hnist.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** user_reminders 表行映射：用户自定义健康提醒。 */
@Data
public class UserReminder {

    private Long id;

    private Integer userId;

    private Integer petId;

    private String title;

    /** 提醒类型，默认 health */
    private String reminderType;

    private LocalDateTime remindAt;

    private String note;

    /** active、notified、completed */
    private String status;

    private LocalDateTime createdAt;
}
