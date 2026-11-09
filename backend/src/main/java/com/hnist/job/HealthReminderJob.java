package com.hnist.job;

import com.hnist.common.constant.ReminderType;
import com.hnist.service.HealthReminderService;
import com.hnist.vo.HealthReminderResponse;
import com.hnist.mapper.UserMapper;
import com.hnist.event.NotificationEvent;
import com.hnist.entity.UserReminder;
import com.hnist.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 每日宠物健康提醒任务，含两个调度：
 * 1. createDailyReminders：每天 08:00（东八区）为所有普通会员生成疫苗/驱虫/体重提醒通知；
 * 2. createCustomReminderNotifications：每分钟扫描到期的自定义提醒并发通知。
 * 幂等性：通知写入由 dedupeKey 唯一索引收敛，重复执行捕获 DuplicateKeyException 视为已发送；
 * 多实例同时跑同样靠唯一索引去重，最坏情况是一条通知插入成功、其余实例拿到 DuplicateKey 跳过。
 * 失败影响面：单条提醒失败只 log.warn，继续处理其余用户，不影响整轮任务。
 */
@Slf4j
@Component
public class HealthReminderJob {

    // 疫苗/驱虫提前 7 天开始提醒；体重 overdue 回看 7 天（补漏近期漏称的情况）。
    private static final int NOTIFY_DAYS_BEFORE = 7;
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final UserMapper userMapper;
    private final HealthReminderService healthReminderService;
    private final NotificationService notificationService;

    public HealthReminderJob(UserMapper userMapper, HealthReminderService healthReminderService,
                             NotificationService notificationService) {
        this.userMapper = userMapper;
        this.healthReminderService = healthReminderService;
        this.notificationService = notificationService;
    }

    /**
     * 每天 08:00 生成固定健康提醒：逐用户逐提醒计算 dedupeKey 后写通知。
     * 幂等键重复触发 DuplicateKeyException，按「已存在」跳过并记 debug，不算失败。
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Shanghai")
    public void createDailyReminders() {
        for (Integer userId : userMapper.selectNormalMemberUserIds()) {
            List<HealthReminderResponse> reminders = healthReminderService.listReminders(userId);
            for (HealthReminderResponse reminder : reminders) {
                if (!shouldNotify(reminder)) {
                    continue;
                }
                try {
                    String notificationType = resolveNotificationType(reminder);
                    notificationService.create(new NotificationEvent(
                            userId, null, "health", notificationType,
                            resolveNotificationLevel(reminder),
                            reminder.getTitle(), reminder.getDetail(), "pet",
                            reminder.getPetId().longValue(),
                            "/health?petId=" + reminder.getPetId() + "&section=reminders",
                            resolveDedupeKey(userId, reminder)));
                } catch (DuplicateKeyException exception) {
                    log.debug("健康提醒已存在，跳过，用户：{}，类型：{}",
                            userId, reminder.getType());
                } catch (RuntimeException exception) {
                    log.warn("健康提醒生成失败，用户：{}，类型：{}",
                            userId, reminder.getType(), exception);
                }
            }
        }
    }

    /**
     * 每分钟扫描到期自定义提醒：通知发送成功（或确认已存在）才 markNotified 标记已提醒，
     * 发送失败不标记，下一轮扫描自然重试；dedupeKey = health:custom:{提醒id} 防重复投递。
     */
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Shanghai")
    public void createCustomReminderNotifications() {
        for (UserReminder reminder : healthReminderService.listDueCustomReminders()) {
            boolean handled = false;
            try {
                notificationService.create(new NotificationEvent(
                        reminder.getUserId(), null, "health", "custom_due", "info",
                        reminder.getTitle(), customReminderContent(reminder),
                        "user_reminder", reminder.getId(),
                        "/health?petId=" + reminder.getPetId() + "&section=reminders",
                        "health:custom:" + reminder.getId()));
                handled = true;
            } catch (DuplicateKeyException exception) {
                handled = true;
                log.debug("自定义健康提醒已存在，跳过，提醒ID：{}", reminder.getId());
            } catch (RuntimeException exception) {
                log.warn("自定义健康提醒生成失败，提醒ID：{}", reminder.getId(), exception);
            }
            if (handled) {
                markNotified(reminder.getId());
            }
        }
    }

    /**
     * 通知窗口：体重提醒看「 overdue 7 天内 ~ 今天」，疫苗/驱虫看「今天 ~ 未来 7 天」（提前预警）。
     * CUSTOM 类型由 createCustomReminderNotifications 专门处理，这里跳过；日期解析失败按不该打扰处理。
     */
    private boolean shouldNotify(HealthReminderResponse reminder) {
        if (reminder.getPetId() == null || ReminderType.CUSTOM.equals(reminder.getType())) {
            return false;
        }
        try {
            LocalDate dueDate = LocalDate.parse(reminder.getDueDate());
            LocalDate today = LocalDate.now(ZONE_ID);
            if (ReminderType.WEIGHT.equals(reminder.getType())) {
                return !dueDate.isBefore(today.minusDays(NOTIFY_DAYS_BEFORE))
                        && !dueDate.isAfter(today);
            }
            if (!ReminderType.VACCINE.equals(reminder.getType())
                    && !ReminderType.DEWORMING.equals(reminder.getType())) {
                return false;
            }
            return !dueDate.isBefore(today)
                    && !dueDate.isAfter(today.plusDays(NOTIFY_DAYS_BEFORE));
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private String resolveNotificationType(HealthReminderResponse reminder) {
        return ReminderType.WEIGHT.equals(reminder.getType())
                ? "weight_alert" : reminder.getType().toLowerCase() + "_due";
    }

    private String resolveNotificationLevel(HealthReminderResponse reminder) {
        return ReminderType.LEVEL_CONSULT_VET.equals(reminder.getLevel())
                ? "warning" : "info";
    }

    /**
     * 幂等键 = health:{userId}:{type}:{petId}:{dueDate}，同一天同宠物的同类提醒只发一次。
     * WEIGHT 额外拼 level 与详情 hash：同一天可能产出多档（WEIGHT_ALERT/CONSULT_VET）不同的体重告警，共用前缀会互相吞掉。
     */
    private String resolveDedupeKey(Integer userId, HealthReminderResponse reminder) {
        String prefix = "health:" + userId + ":" + reminder.getType() + ":"
                + reminder.getPetId() + ":" + reminder.getDueDate();
        if (ReminderType.WEIGHT.equals(reminder.getType())) {
            return prefix + ":" + reminder.getLevel() + ":"
                    + Integer.toHexString(reminder.getDetail().hashCode());
        }
        return prefix;
    }

    /** 标记失败只记日志：通知已发出，下轮重试会拿到 DuplicateKey 跳过，不会重复打扰用户。 */
    private void markNotified(Long reminderId) {
        try {
            healthReminderService.markCustomReminderNotified(reminderId);
        } catch (RuntimeException exception) {
            log.warn("自定义健康提醒状态更新失败，提醒ID：{}", reminderId, exception);
        }
    }

    private String customReminderContent(UserReminder reminder) {
        String note = reminder.getNote() == null || reminder.getNote().isBlank()
                ? "自定义健康提醒" : reminder.getNote();
        return note + "\n提醒时间：" + reminder.getRemindAt();
    }
}
