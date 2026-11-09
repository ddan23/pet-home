package com.hnist.mapper;

import com.hnist.entity.UserReminder;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 用户自定义提醒数据访问。 */
public interface UserReminderMapper {

    int insert(UserReminder reminder);

    List<UserReminder> selectByUserId(@Param("userId") Integer userId);

    /** 只捞 status = 'active'、remind_at 已到且用户为 normal 角色（role = 'user'）的提醒，单次最多 100 条。 */
    List<UserReminder> selectDueUnnotified();

    UserReminder selectByIdAndUserId(@Param("id") Long id,
                                     @Param("userId") Integer userId);

    int updateOwn(@Param("id") Long id,
                  @Param("userId") Integer userId,
                  @Param("petId") Integer petId,
                  @Param("title") String title,
                  @Param("remindAt") LocalDateTime remindAt,
                  @Param("note") String note);

    /** 条件更新：仅 status = 'active' 可置为 'notified'，保证已通知的提醒不会被定时任务重复推送。 */
    int markNotified(@Param("id") Long id);

    /** 状态机推进：'active'/'notified' 才可置为 'completed'，并限定 user_id 归属。 */
    int completeOwn(@Param("id") Long id,
                    @Param("userId") Integer userId);

    int deleteOwn(@Param("id") Long id,
                  @Param("userId") Integer userId);
}
