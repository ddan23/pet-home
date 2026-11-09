package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 宠物健康提醒：疫苗/驱虫/体重提醒每次按档案实时推算，只有自定义提醒落 user_reminders。 */
@Data
@AllArgsConstructor
public class HealthReminderResponse {

    /** 仅自定义提醒有值（user_reminders 主键）；推算类提醒不落库，恒为 null。 */
    private Long reminderId;

    private Integer petId;

    /** 宠物档案数组下标；-1 表示该宠物已删除，仅出现在仍保留的自定义提醒上。 */
    private Integer petIndex;

    private String petName;

    /** 取值 VACCINE、DEWORMING、WEIGHT、CUSTOM。 */
    private String type;

    /** 取值 OVERDUE、DUE_SOON、PLANNED，以及体重提醒策略输出的 CONSULT_VET、WEIGHT_ALERT。 */
    private String level;

    private String title;

    private String detail;

    /** 到期日，格式 yyyy-MM-dd；自定义提醒无日期时为 null，排序时排在最后。 */
    private String dueDate;

    /** 提醒时间字符串，仅自定义提醒有值，其余为 null。 */
    private String remindAt;

    /** 自定义提醒的启停状态，推算类固定 active。 */
    private String status;
}
