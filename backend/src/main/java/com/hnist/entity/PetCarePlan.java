package com.hnist.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** pet_care_plans 表行映射：宠物待完成照护计划。 */
@Data
public class PetCarePlan {

    private Long id;

    private Integer petId;

    /** vaccine 或 deworming */
    private String careType;

    private String title;

    /** 计划执行日，疫苗与驱虫的到期提醒以此为基准 */
    private LocalDate plannedOn;

    /** 新增固定写 pending，待办查询只取该值 */
    private String status;

    private String note;

    private LocalDateTime completedAt;
}
