package com.hnist.mapper;

import com.hnist.entity.Post;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 社区帖子数据访问：帖子 CRUD、状态审核与计数维护。 */
public interface PostMapper {

    // 根据帖子ID查询帖子详情
    Post selectById(@Param("id") Integer id);

    // 分页查询点赞列表中的公开帖子（仅 status = 'normal' 且未软删除的会返回）
    List<Post> selectPublicByIds(@Param("ids") List<Integer> ids,
                                 @Param("offset") Integer offset,
                                 @Param("limit") Integer limit);

    // 统计点赞列表中仍公开的帖子
    int countPublicByIds(@Param("ids") List<Integer> ids);

    // 分页查询帖子列表（按分类和关键词筛选，仅正常状态）
    List<Post> selectByCategory(@Param("category") String category,
                                @Param("keyword") String keyword,
                                @Param("status") String status,
                                @Param("offset") Integer offset,
                                @Param("limit") Integer limit);

    // 统计帖子数量（与selectByCategory筛选条件一致）
    int countByCategory(@Param("category") String category, @Param("keyword") String keyword,
                        @Param("status") String status);

    // 分页查询用户发布的帖子列表
    List<Post> selectByUserId(@Param("userId") Integer userId, @Param("status") String status,
                              @Param("offset") Integer offset, @Param("limit") Integer limit);

    // 统计用户发布的帖子总数
    int countByUserId(@Param("userId") Integer userId, @Param("status") String status);

    // 分页查询所有帖子（管理员视角）
    List<Post> selectAllForAdmin(@Param("keyword") String keyword, @Param("status") String status,
                                 @Param("blockedType") String blockedType,
                                 @Param("category") String category, @Param("offset") Integer offset,
                                 @Param("limit") Integer limit);

    // 统计所有帖子数量（管理员视角）
    int countAllForAdmin(@Param("keyword") String keyword, @Param("status") String status,
                         @Param("blockedType") String blockedType, @Param("category") String category);

    // 发布新帖子
    int insert(Post post);

    // 软删除帖子
    int deleteById(@Param("id") Integer id);

    // 按当前状态条件更新帖子状态，避免并发审核互相覆盖
    int updateStatusIfCurrent(@Param("id") Integer id,
                              @Param("currentStatus") String currentStatus,
                              @Param("status") String status);

    // 更新帖子点赞数（delta 为正数加、负数减，GREATEST 保证不为负）
    int updateLikesCount(@Param("id") Integer id, @Param("delta") Integer delta);

    // 更新帖子评论数（delta为正数加，负数减）
    int updateCommentsCount(@Param("id") Integer id, @Param("delta") Integer delta);

    // 按状态统计帖子数量
    List<Map<String, Object>> statsByStatus();

    // 统计今日新增帖子数量
    int countToday();
}
