package com.hnist.service.impl;

import com.hnist.common.exception.BusinessException;
import com.hnist.domain.WeightTrendAlert;
import com.hnist.dto.SaveHealthReminderRequest;
import com.hnist.entity.UserReminder;
import com.hnist.event.NotificationEvent;
import com.hnist.mapper.UserReminderMapper;
import com.hnist.policy.WeightTrendPolicy;
import com.hnist.entity.PetWeightRecord;
import com.hnist.service.HealthReminderService;
import com.hnist.service.NotificationService;
import com.hnist.service.UserService;
import com.hnist.vo.HealthReminderResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.hnist.common.constant.ReminderType;
import java.util.Map;
import java.util.HashMap;

/** 宠物健康提醒的实时推算与自定义提醒维护。 */
@Service
@Slf4j
public class HealthReminderServiceImpl implements HealthReminderService {

    // 提前 7 天进入“即将到期”档：页面提示和 HealthReminderJob 的推送窗口必须同步，改这里要同时改任务里的 NOTIFY_DAYS_BEFORE。
    private static final int DUE_SOON_DAYS = 7;
    // 到期判定统一按东八区日历日比较：部署机时区不同会让同一条计划提前或延后一天变成过期。
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final String LEVEL_OVERDUE = "OVERDUE";
    private static final String LEVEL_DUE_SOON = "DUE_SOON";
    private static final String LEVEL_PLANNED = "PLANNED";
    private static final String LEVEL_STANDING = "STANDING";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_COMPLETED = "completed";
    private static final String HEALTH_NOTIFICATION_TYPE = "health_reminder";

    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final UserReminderMapper userReminderMapper;
    private final NotificationService notificationService;

    public HealthReminderServiceImpl(UserService userService, ObjectMapper objectMapper,
                                     UserReminderMapper userReminderMapper,
                                     NotificationService notificationService) {
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.userReminderMapper = userReminderMapper;
        this.notificationService = notificationService;
    }

    @Override
    public List<HealthReminderResponse> listReminders(Integer userId) {
        try {
            // 疫苗、驱虫、体重提醒不落库，每次按档案现算：档案一改提醒立刻同步，也不会像入库那样攒出重复行；只有用户自建的 custom 提醒存 user_reminders。
            JsonNode pets = loadPets(userId);
            List<HealthReminderResponse> reminders = new ArrayList<>();
            if (!pets.isArray()) {
                return reminders;
            }
            Map<Integer, Integer> petIndexById = new HashMap<>();
            Map<Integer, String> petNameById = new HashMap<>();
            for (int index = 0; index < pets.size(); index++) {
                JsonNode pet = pets.get(index);
                int petId = pet.path("id").asInt();
                collectPetReminders(petId, index, pet, reminders);
                petIndexById.put(petId, index);
                petNameById.put(petId, pet.path("name").asText("宠物"));
            }
            collectCustomReminders(userId, petIndexById, petNameById, reminders);
            // 按 dueDate 升序、无日期排最后：没有 remind_at 的“长期有效”自定义提醒不能挤到最前面。
            reminders.sort(Comparator.comparing(HealthReminderResponse::getDueDate,
                    Comparator.nullsLast(String::compareTo)));
            return reminders;
        } catch (JsonProcessingException exception) {
            // 档案 JSON 解析失败时返回空列表而不是抛错：健康页其他板块和每日提醒任务不该被一个坏档案拖垮。
            log.warn("解析宠物健康提醒失败，用户：{}", userId, exception);
            return List.of();
        }
    }

    private void collectCustomReminders(Integer userId, Map<Integer, Integer> petIndexById,
                                        Map<Integer, String> petNameById,
                                        List<HealthReminderResponse> reminders) {
        // 自定义提醒的宠物下标从本次档案结果反查：查不到时用 -1 加“已删除宠物”占位，让提醒仍然返回，而不是因为档案缺失就整条消失。
        for (UserReminder reminder : userReminderMapper.selectByUserId(userId)) {
            Integer petIndex = petIndexById.getOrDefault(reminder.getPetId(), -1);
            LocalDate dueDate = reminder.getRemindAt() == null
                    ? null : reminder.getRemindAt().toLocalDate();
            String level = resolveCustomLevel(reminder.getStatus(), reminder.getRemindAt());
            String petName = petNameById.getOrDefault(reminder.getPetId(), "已删除宠物");
            reminders.add(new HealthReminderResponse(
                    reminder.getId(), reminder.getPetId(), petIndex, petName,
                    ReminderType.CUSTOM, level, reminder.getTitle(),
                    reminder.getNote() == null ? "自定义健康提醒" : reminder.getNote(),
                    dueDate == null ? null : dueDate.toString(),
                    reminder.getRemindAt() == null ? null : reminder.getRemindAt().toString(),
                    reminder.getStatus()));
        }
    }

    private void collectPetReminders(int petId, int index, JsonNode pet,
                                     List<HealthReminderResponse> reminders) {
        String petName = pet.path("name").asText("未命名宠物");
        collectWeightReminder(petId, index, petName, pet, reminders);
        // 疫苗与驱虫的到期基准日是 carePlans.plannedOn（本次计划执行日），
        // 不读 pet_vaccinations.next_due_on / pet_deworming_records.next_due_on：
        // 提醒跟着“下一次要做的事”走，补录接种记录不会自动顺延提醒。
        JsonNode carePlans = pet.path("carePlans");
        if (!carePlans.isArray()) {
            return;
        }
        // 只认 vaccine 与 deworming 两种 care_type：其他类型不生成到期提醒，避免凭空认定体检等项目的周期。
        for (JsonNode plan : carePlans) {
            String careType = plan.path("careType").asText("");
            String title = plan.path("title").asText("照护计划");
            String note = plan.path("note").asText("");
            if ("vaccine".equals(careType)) {
                addDateReminder(reminders, petId, index, petName, ReminderType.VACCINE, "疫苗",
                        title, note, plan.path("plannedOn").asText(null));
            } else if ("deworming".equals(careType)) {
                addDateReminder(reminders, petId, index, petName, ReminderType.DEWORMING, "驱虫",
                        title, note, plan.path("plannedOn").asText(null));
            }
        }
    }

    private void collectWeightReminder(int petId, int index, String petName,
                                       JsonNode pet,
                                       List<HealthReminderResponse> reminders) {
        JsonNode records = pet.path("weightRecords");
        if (!records.isArray()) {
            return;
        }
        List<PetWeightRecord> weightRecords = new ArrayList<>(records.size());
        for (JsonNode record : records) {
            BigDecimal weight = decimal(record.path("weight"));
            LocalDate recordedOn = parseDate(record.path("date").asText(null));
            // 解析不出日期或重量的记录直接跳过：一条脏数据不该让这只宠物整条体重提醒消失。
            if (weight == null || recordedOn == null) {
                continue;
            }
            PetWeightRecord item = new PetWeightRecord();
            item.setWeight(weight);
            item.setRecordedOn(recordedOn);
            weightRecords.add(item);
        }
        LocalDate birthDate = parseDate(pet.path("birthDate").asText(null));
        // 变化幅度阈值全部由 WeightTrendPolicy 判定（7 天 5%/10%、30 天 10%/15%、最近一次称重要在 7 天内、幼年增重不提示），这里只提供记录和东八区的“今天”。
        WeightTrendPolicy.analyze(weightRecords, birthDate, LocalDate.now(ZONE_ID))
                .ifPresent(alert -> reminders.add(createWeightReminder(
                        petId, index, petName, alert)));
    }

    private HealthReminderResponse createWeightReminder(
            int petId, int index, String petName, WeightTrendAlert alert) {
        // level 直接透传策略输出的 CONSULT_VET / WEIGHT_ALERT，HealthReminderJob 靠它决定通知级别是 warning 还是 info。
        String title = ReminderType.LEVEL_CONSULT_VET.equals(alert.level())
                ? "体重变化明显" : "体重变化提示";
        return new HealthReminderResponse(
                null, petId, index, petName, ReminderType.WEIGHT, alert.level(),
                title, alert.detail(), alert.recordDate().toString(),
                null, STATUS_ACTIVE);
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void addDateReminder(List<HealthReminderResponse> reminders, int petId,
                                 int index, String petName, String type, String label,
                                 String itemName, String note, String dateText) {
        LocalDate dueDate = parseDate(dateText);
        if (dueDate == null) {
            return;
        }
        LocalDate today = LocalDate.now(ZONE_ID);
        String detail = note == null || note.isBlank()
                ? itemName + "安排在 " + dueDate
                : itemName + "安排在 " + dueDate + "，" + note;
        // 过期判断用严格的 isBefore，所以到期当天仍算 DUE_SOON；提前 DUE_SOON_DAYS 天进入“即将到期”，窗口与页面提示、推送任务一致。
        if (dueDate.isBefore(today)) {
            reminders.add(new HealthReminderResponse(
                    null, petId, index, petName, type, LEVEL_OVERDUE,
                    label + "已到期", detail + "，请尽快联系兽医",
                    dueDate.toString(), null, STATUS_ACTIVE));
        } else {
            boolean dueSoon = !dueDate.isAfter(today.plusDays(DUE_SOON_DAYS));
            reminders.add(new HealthReminderResponse(
                    null, petId, index, petName, type,
                    dueSoon ? LEVEL_DUE_SOON : LEVEL_PLANNED,
                    dueSoon ? label + "即将到期" : label + "计划",
                    dueSoon ? detail + "，请提前预约" : detail,
                    dueDate.toString(), null, STATUS_ACTIVE));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createReminder(Integer userId, SaveHealthReminderRequest request) {
        // 宠物归属与时间都先校验：提醒只能挂在当前用户名下的宠物上，remind_at 必须晚于当前时间，否则分钟级任务会立刻把这条补发成通知。
        validateReminder(userId, request);
        UserReminder reminder = new UserReminder();
        reminder.setUserId(userId);
        reminder.setPetId(request.getPetId());
        reminder.setTitle(request.getTitle().trim());
        // 固定记为 custom，和档案推算出的 VACCINE/DEWORMING/WEIGHT 区分开：
        // HealthReminderJob 的 shouldNotify 显式跳过 CUSTOM，自定义提醒的推送只由 remind_at 到期扫描负责。
        reminder.setReminderType("custom");
        reminder.setRemindAt(request.getRemindAt());
        reminder.setNote(trimToNull(request.getNote()));
        reminder.setStatus(STATUS_ACTIVE);
        userReminderMapper.insert(reminder);
        // 没有 remind_at 的行永远不会被 selectDueUnnotified 扫到，通知只在这里产生一次；
        // dedupe_key 用 :standing 后缀，与任务侧 health:custom:{id} 区分，避免两条来源抢同一个去重键。
        if (request.getRemindAt() == null) {
            createStandingReminderNotification(reminder);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReminder(Integer userId, Long reminderId,
                               SaveHealthReminderRequest request) {
        UserReminder reminder = requireOwnedReminder(userId, reminderId);
        // 只有 active 可以改：notified 已经发过站内通知、completed 用户已确认，再改时间会让通知内容和提醒对不上。
        if (!STATUS_ACTIVE.equals(reminder.getStatus())) {
            throw new BusinessException("已通知或已完成的提醒不能修改");
        }
        validateReminder(userId, request);
        // updateOwn 的 where 带 status = 'active'：影响 0 行说明状态被并发改掉，报错让前端刷新而不是静默覆盖别人的修改。
        if (userReminderMapper.updateOwn(reminderId, userId, request.getPetId(),
                request.getTitle().trim(), request.getRemindAt(),
                trimToNull(request.getNote())) == 0) {
            throw new BusinessException("提醒状态已变化，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeReminder(Integer userId, Long reminderId) {
        requireOwnedReminder(userId, reminderId);
        // completeOwn 只接受 active/notified：已完成再点一次影响 0 行，转成业务提示而不是重复置位。
        if (userReminderMapper.completeOwn(reminderId, userId) == 0) {
            throw new BusinessException("提醒已完成或状态已变化");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReminder(Integer userId, Long reminderId) {
        requireOwnedReminder(userId, reminderId);
        // 删除不看状态（deleteOwn 只按 id + user_id），已完成或已通知的提醒也允许用户自己清理。
        if (userReminderMapper.deleteOwn(reminderId, userId) == 0) {
            throw new BusinessException("提醒不存在");
        }
    }

    @Override
    public List<UserReminder> listDueCustomReminders() {
        // 到期由 SQL 的 remind_at <= NOW() 判定并只取 status='active'：以数据库时钟为准，多实例部署时不会因各节点本地时间不同而提前或重复推送。
        return userReminderMapper.selectDueUnnotified();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markCustomReminderNotified(Long reminderId) {
        // markNotified 是条件更新（where status='active'），同一条被重复扫描只会成功一次；调用方在通知插入成功或已存在之后才调，保证先有通知再改状态。
        userReminderMapper.markNotified(reminderId);
    }

    private void validateReminder(Integer userId, SaveHealthReminderRequest request) {
        // 归属校验走 userService.getPets 的档案 JSON：pet_id 必须出现在当前用户的宠物列表里，否则可以给别人的宠物挂提醒。
        requireOwnedPet(userId, request.getPetId());
        if (request.getRemindAt() != null
                && !request.getRemindAt().isAfter(LocalDateTime.now(ZONE_ID))) {
            throw new BusinessException("提醒时间必须晚于当前时间");
        }
    }

    private void createStandingReminderNotification(UserReminder reminder) {
        String content = reminder.getNote() == null
                ? "无固定期限健康提醒，完成后请标记为已完成。" : reminder.getNote();
        notificationService.create(new NotificationEvent(
                reminder.getUserId(), null, "health", HEALTH_NOTIFICATION_TYPE, "info",
                "健康提醒：" + reminder.getTitle(), content,
                "user_reminder", reminder.getId(),
                "/health?petId=" + reminder.getPetId() + "&section=reminders",
                "health:custom:" + reminder.getId() + ":standing"));
    }

    private void requireOwnedPet(Integer userId, Integer petId) {
        if (petId == null) {
            throw new BusinessException("宠物档案不存在");
        }
        try {
            JsonNode pets = loadPets(userId);
            if (!pets.isArray()) {
                throw new BusinessException("宠物档案不存在");
            }
            for (JsonNode pet : pets) {
                if (pet.path("id").asInt() == petId) {
                    return;
                }
            }
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "读取宠物档案失败");
        }
        throw new BusinessException("宠物档案不存在");
    }

    private JsonNode loadPets(Integer userId) throws JsonProcessingException {
        // 档案从 UserService.getPets 的 JSON 结果解析：健康模块不直接查 pets 表，字段口径与档案接口保持一致。
        return objectMapper.readTree(userService.getPets(userId).getPets());
    }

    private UserReminder requireOwnedReminder(Integer userId, Long reminderId) {
        // 查询条件同时带 id 与 user_id，查不到统一返回 404：不区分“不存在”和“不是你的”，避免用 ID 探测他人提醒。
        UserReminder reminder = userReminderMapper.selectByIdAndUserId(reminderId, userId);
        if (reminder == null) {
            throw new BusinessException(404, "提醒不存在");
        }
        return reminder;
    }

    private String resolveCustomLevel(String status, LocalDateTime remindAt) {
        // 列表里的 level 不落库，由 status 与 remind_at 现场派生：completed 直接标记完成，remind_at 为空即长期有效提醒。
        if (STATUS_COMPLETED.equals(status)) {
            return "COMPLETED";
        }
        if (remindAt == null) {
            return LEVEL_STANDING;
        }
        // 展示用的过期判断走应用时钟，只影响文案；真正决定是否推送的 selectDueUnnotified 用数据库 NOW()，展示容忍秒级误差、推送不容忍。
        return remindAt.isBefore(LocalDateTime.now(ZONE_ID)) ? LEVEL_OVERDUE : LEVEL_DUE_SOON;
    }

    private String trimToNull(String value) {
        return value == null ? null : value.trim();
    }

    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
