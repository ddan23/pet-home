package com.hnist.service;

import com.hnist.dto.SaveHealthReminderRequest;
import com.hnist.entity.UserReminder;
import com.hnist.vo.HealthReminderResponse;

import java.util.List;

/** 宠物健康提醒的查询与自定义提醒维护。 */
public interface HealthReminderService {

    /** 档案内的疫苗/驱虫/体重提醒每次实时推算，不落库；custom 提醒来自 user_reminders，解析失败返回空列表。 */
    List<HealthReminderResponse> listReminders(Integer userId);

    /** remindAt 为空表示长期有效提醒，此时不排期，由实现直接生成一条站内通知。 */
    void createReminder(Integer userId, SaveHealthReminderRequest request);

    /** 提醒已通知、已完成或状态被并发改写时抛 BusinessException，调用方需重新拉取列表。 */
    void updateReminder(Integer userId, Long reminderId,
                        SaveHealthReminderRequest request);

    /** 条件更新只接受 active/notified：已完成或状态被并发改写时抛 BusinessException，重复点击不会二次置位。 */
    void completeReminder(Integer userId, Long reminderId);

    /** 删除不校验提醒状态，已通知或已完成的也允许清理；非本人或不存在抛业务异常。 */
    void deleteReminder(Integer userId, Long reminderId);

    /** 供 HealthReminderJob 分钟级扫描：到期口径由 SQL 的 NOW() 与 status='active' 决定。 */
    List<UserReminder> listDueCustomReminders();

    /** 条件更新 status active → notified，重复调用幂等；调用方需先确保通知已写入。 */
    void markCustomReminderNotified(Long reminderId);
}
