package com.hnist.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 体重趋势提示：由 WeightTrendPolicy 判定后转成健康页提醒，阈值改动集中在策略里。 */
public record WeightTrendAlert(
        /** 取值 CONSULT_VET 或 WEIGHT_ALERT，HealthReminderJob 据此决定推送级别。 */
        String level,
        /** 变化方向，GAIN 或 LOSS，按本次与基线的大小关系判定。 */
        String direction,
        /** 最新一次体重，单位 kg。 */
        BigDecimal currentWeight,
        /** 对比基线体重，单位 kg，取窗口内最早的一条。 */
        BigDecimal baselineWeight,
        /** 变化幅度百分比，取绝对值、保留 1 位小数、四舍五入，方向另由 direction 表达。 */
        BigDecimal changePercent,
        /** 最新称重日期，同时作为提醒的 dueDate。 */
        LocalDate recordDate,
        String detail) {
}
