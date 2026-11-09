package com.hnist.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.time.LocalDateTime;

/** users 表行映射：账号基本信息与角色状态，登录态与权限判断都以这里的 role、status 为准。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private String username;

    @JsonIgnore
    @ToString.Exclude
    private String password;
    /** 角色：user、business、admin（AccountRole），TokenInterceptor 按此匹配 RequireRole */
    private String role;
    private String nickname;
    private String phone;
    private String email;
    private String city;
    private String avatar;
    /** 账号状态：normal、disabled；非 normal 的账号在 TokenInterceptor 中解析不出登录用户 */
    private String status;
    /** JSON 数组，元素为收货地址对象（name、phone、province、city、district、detail） */
    private String addresses;
    /** JSON 数组：[post_id, ...]，记录用户点赞过的帖子 */
    private String likedPosts;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 非DB字段：shopCount、rating 由 UserMapper 子查询带回，postCount、followCount、fanCount 由 UserServiceImpl 统计后填充 */
    private Integer shopCount;
    private Double rating;
    private Integer postCount;
    private Integer followCount;
    private Integer fanCount;
}
