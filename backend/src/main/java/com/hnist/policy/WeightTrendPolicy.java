package com.hnist.policy;

import com.hnist.common.constant.ReminderType;
import com.hnist.domain.WeightTrendAlert;
import com.hnist.entity.PetWeightRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 体重变化提示策略：按 7 天/30 天两个窗口对比变化幅度，超阈值才产出告警。
 * 阈值口径（单位均为百分比）由 HealthReminderServiceImpl 与本类共同依赖，调整需两侧同步评估。
 */
public final class WeightTrendPolicy {

    // 以下百分比为相对基线记录的体重变化幅度阈值：急性窗口 5% 提示、10% 建议就医；月度窗口放宽到 10%/15%。
    private static final int ACUTE_DAYS = 7;
    private static final int MONTH_DAYS = 30;
    private static final int ALERT_PERCENT = 5;
    private static final int MONTH_ALERT_PERCENT = 10;
    private static final int URGENT_PERCENT = 10;
    private static final int MONTH_URGENT_PERCENT = 15;
    // 最近一次称重必须落在最近 7 天内：更旧的记录无法反映当前状况，提示只会误导。
    private static final int RECENT_RECORD_DAYS = 7;

    private WeightTrendPolicy() {
    }

    /**
     * 主入口：归并同日重复记录（同日以最后一次为准）后，不足 2 条无法算基线直接返回空；
     * 最近一次称重超出 7 天窗口也返回空，避免拿旧数据造出过期告警。
     * 7 天与 30 天两个窗口各算一次，取优先级更高的结果；幼年宠物（<365 天）的增重视为正常生长不提示。
     */
    public static Optional<WeightTrendAlert> analyze(List<PetWeightRecord> records,
                                                     LocalDate birthDate,
                                                     LocalDate today) {
        Map<LocalDate, BigDecimal> weightsByDate = normalize(records);
        if (weightsByDate.size() < 2) {
            return Optional.empty();
        }
        List<Map.Entry<LocalDate, BigDecimal>> entries =
                new ArrayList<>(weightsByDate.entrySet());
        Map.Entry<LocalDate, BigDecimal> latest = entries.get(entries.size() - 1);
        if (latest.getKey().isAfter(today)
                || latest.getKey().isBefore(today.minusDays(RECENT_RECORD_DAYS))) {
            return Optional.empty();
        }
        WeightTrendAlert acute = compare(entries, latest, ACUTE_DAYS,
                ALERT_PERCENT, URGENT_PERCENT);
        WeightTrendAlert monthly = compare(entries, latest, MONTH_DAYS,
                MONTH_ALERT_PERCENT, MONTH_URGENT_PERCENT);
        WeightTrendAlert alert = higherPriority(acute, monthly);
        if (alert == null || isGrowingPetGain(alert, birthDate, today)) {
            return Optional.empty();
        }
        return Optional.of(alert);
    }

    /** 同一天多条记录取最后写入的一条：TreeMap put 覆盖旧值，避免同日记录被当成多次变化。 */
    private static Map<LocalDate, BigDecimal> normalize(List<PetWeightRecord> records) {
        Map<LocalDate, BigDecimal> result = new TreeMap<>();
        if (records == null) {
            return result;
        }
        for (PetWeightRecord record : records) {
            if (record.getRecordedOn() != null && record.getWeight() != null
                    && record.getWeight().compareTo(BigDecimal.ZERO) > 0) {
                result.put(record.getRecordedOn(), record.getWeight());
            }
        }
        return result;
    }

    /**
     * 在 days 窗口内选变化幅度最大的记录作基线：提示的是最显著变化而不是最早记录，漏掉显著变化就失去提醒意义。
     * 达到 urgentPercent 升级为 CONSULT_VET，否则达到 alertPercent 给 WEIGHT_ALERT，均未达到返回 null。
     */
    private static WeightTrendAlert compare(
            List<Map.Entry<LocalDate, BigDecimal>> entries,
            Map.Entry<LocalDate, BigDecimal> latest,
            int days, int alertPercent, int urgentPercent) {
        LocalDate threshold = latest.getKey().minusDays(days);
        Map.Entry<LocalDate, BigDecimal> baseline = null;
        BigDecimal percent = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, BigDecimal> entry : entries) {
            if (entry.getKey().isBefore(threshold)) {
                continue;
            }
            if (entry == latest) {
                break;
            }
            BigDecimal candidate = changePercent(entry.getValue(), latest.getValue());
            if (candidate.compareTo(percent) > 0) {
                baseline = entry;
                percent = candidate;
            }
        }
        if (baseline == null) {
            return null;
        }
        long intervalDays = Math.max(1,
                ChronoUnit.DAYS.between(baseline.getKey(), latest.getKey()));
        if (percent.compareTo(BigDecimal.valueOf(urgentPercent)) >= 0) {
            return alert(ReminderType.LEVEL_CONSULT_VET, baseline, latest, percent, intervalDays);
        }
        if (percent.compareTo(BigDecimal.valueOf(alertPercent)) >= 0) {
            return alert(ReminderType.LEVEL_WEIGHT_ALERT, baseline, latest, percent, intervalDays);
        }
        return null;
    }

    private static WeightTrendAlert alert(
            String level, Map.Entry<LocalDate, BigDecimal> baseline,
            Map.Entry<LocalDate, BigDecimal> latest,
            BigDecimal percent, long intervalDays) {
        int comparison = latest.getValue().compareTo(baseline.getValue());
        String direction = comparison >= 0 ? "GAIN" : "LOSS";
        String action = "LOSS".equals(direction)
                ? "建议结合精神、食欲、饮水和排泄情况尽快咨询兽医。"
                : "建议核实称重结果，并结合体况和近期饮食变化咨询兽医。";
        String detail = "近" + intervalDays + "天体重从" + baseline.getValue()
                + "kg变化到" + latest.getValue() + "kg，"
                + ("LOSS".equals(direction) ? "下降" : "上升")
                + percent + "%。称重误差也可能造成变化，" + action;
        return new WeightTrendAlert(level, direction, latest.getValue(),
                baseline.getValue(), percent, latest.getKey(), detail);
    }

    /** 变化幅度取绝对值：涨跌同权重，方向由 alert() 单独判定；HALF_UP 保留 1 位小数，避免除不尽产生超长小数。 */
    private static BigDecimal changePercent(BigDecimal baseline, BigDecimal current) {
        return current.subtract(baseline).abs()
                .multiply(BigDecimal.valueOf(100))
                .divide(baseline, 1, RoundingMode.HALF_UP);
    }

    /** 两个窗口都命中时取更严重的：CONSULT_VET 优先于 WEIGHT_ALERT。 */
    private static WeightTrendAlert higherPriority(WeightTrendAlert first,
                                                   WeightTrendAlert second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return ReminderType.LEVEL_CONSULT_VET.equals(first.level()) ? first : second;
    }

    /** 幼年宠物（出生不足一年）增重属于正常发育，不产出 GAIN 告警，避免每天误报。 */
    private static boolean isGrowingPetGain(WeightTrendAlert alert,
                                            LocalDate birthDate,
                                            LocalDate today) {
        return birthDate != null
                && ChronoUnit.DAYS.between(birthDate, today) < 365
                && "GAIN".equals(alert.direction());
    }
}
