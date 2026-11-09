package com.hnist.mapper;

import com.hnist.entity.Notification;
import com.hnist.vo.NotificationCategoryCount;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 通知数据访问。 */
public interface NotificationMapper {

    Notification selectById(@Param("id") Long id);

    List<Notification> selectByReceiver(@Param("receiverId") Integer receiverId,
                                        @Param("categories") List<String> categories,
                                        @Param("types") List<String> types,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    int countByReceiver(@Param("receiverId") Integer receiverId,
                        @Param("categories") List<String> categories,
                        @Param("types") List<String> types);

    List<Notification> selectAll(@Param("type") String type,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    int countAll(@Param("type") String type);

    int countUnread(@Param("receiverId") Integer receiverId);

    List<NotificationCategoryCount> selectUnreadCountsByCategory(
            @Param("receiverId") Integer receiverId,
            @Param("categories") List<String> categories);

    int insert(Notification notification);

    int insertBatch(@Param("list") List<Notification> notifications);

    // 首次阅读时间用 COALESCE 保留，重复标记不会覆盖原 read_at
    int markRead(@Param("id") Long id, @Param("receiverId") Integer receiverId);

    int markAllRead(@Param("receiverId") Integer receiverId);

    // 软删除：置 deleted_at 而非物理删除
    int deleteById(@Param("id") Long id);

    int deleteOwnById(@Param("id") Long id, @Param("receiverId") Integer receiverId);

    // 只清理 is_read = 1 的通知，未读不会被误删
    int deleteAllRead(@Param("receiverId") Integer receiverId);

    List<Integer> selectUserIdsByRole(@Param("role") String role);

    List<Integer> selectMemberUserIds();
}
