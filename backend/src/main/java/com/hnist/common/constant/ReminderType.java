package com.hnist.common.constant;

/**
 * 健康提醒的类型与级别编码：落 user_reminders.type 与响应体的 level，
 * 提醒服务、定时任务与体重策略共用同一套字面量，避免各处各写一份字符串。
 */
public final class ReminderType {

    /** 疫苗提醒：由 carePlans.care_type = vaccine 的计划生成。 */
    public static final String VACCINE = "VACCINE";

    /** 驱虫提醒：由 carePlans.care_type = deworming 的计划生成。 */
    public static final String DEWORMING = "DEWORMING";

    /** 体重提醒：由体重记录的变化趋势生成，级别再分 WEIGHT_ALERT 与 CONSULT_VET。 */
    public static final String WEIGHT = "WEIGHT";

    /** 用户自建提醒：来自 user_reminders 表，不走宠物档案推导。 */
    public static final String CUSTOM = "CUSTOM";

    /** 体重变化提示级别：仅提示观察，通知按 info 下发。 */
    public static final String LEVEL_WEIGHT_ALERT = "WEIGHT_ALERT";

    /** 建议就医级别：通知按 warning 下发，前端优先展示就诊提示。 */
    public static final String LEVEL_CONSULT_VET = "CONSULT_VET";

    private ReminderType() {
    }
}
