package com.hnist.mapper;

import com.hnist.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 用户表数据访问：登录凭证、资料、状态与点赞列表等字段维护。 */
public interface UserMapper {

    // 根据用户名查询用户
    User selectByUsername(@Param("username") String username);

    // 根据用户ID查询用户
    User selectById(@Param("id") Integer id);

    // 锁定用户行，用于串行化点赞列表更新
    User selectByIdForUpdate(@Param("id") Integer id);

    int insert(User user);

    // 分页查询用户列表（支持按角色/状态/关键词筛选）
    List<User> selectAll(@Param("role") String role,
                         @Param("status") String status,
                         @Param("keyword") String keyword,
                         @Param("offset") int offset,
                         @Param("limit") int limit);

    // 统计用户总数（与selectAll筛选条件一致）
    int countAll(@Param("role") String role, @Param("status") String status, @Param("keyword") String keyword);

    // 按角色+状态统计用户数（全局，不参与 keyword 筛选）
    int countByRoleAndStatus(@Param("role") String role, @Param("status") String status);

    // 更新用户个人资料
    int updateProfile(User user);

    // 更新用户收货地址
    int updateAddresses(@Param("userId") Integer userId, @Param("addresses") String addresses);

    // 更新用户点赞帖子列表
    int updateLikedPosts(@Param("userId") Integer userId, @Param("likedPosts") String likedPosts);

    // 更新用户头像
    int updateAvatar(@Param("userId") Integer userId, @Param("avatar") String avatar);

    int updatePassword(@Param("userId") Integer userId, @Param("password") String password);

    // 只取 status = 'normal' 且 role = 'user' 的用户 id，用作面向普通会员的群发口径
    List<Integer> selectNormalMemberUserIds();

    // 管理员更新用户状态（正常/禁用）
    int updateStatus(@Param("id") Integer id, @Param("status") String status);
}
