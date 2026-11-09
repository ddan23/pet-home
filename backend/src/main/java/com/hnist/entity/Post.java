package com.hnist.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** posts 表行映射：社区帖子，status 与软删 deleted_at 共同决定可见性。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Post implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer userId;
    /** 晒宠, 求助, 领养, 好物, 日常 */
    private String category;
    private String title;
    private String content;
    /** 图片路径 */
    private String image;
    /** 点赞数，冗余计数列 */
    private Integer likes;
    /** 评论数，冗余计数列 */
    private Integer comments;
    /** normal、pending、blocked */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
    /** 非DB字段，用于前端展示 */
    private String nickname;
    /** 非DB字段，帖子作者头像路径 */
    private String avatar;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    /** 非DB字段，最近一次人工审核状态（ModerationReviewStatus） */
    private String latestReviewStatus;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    /** 非DB字段，管理员端展示状态：published、pending、rejected、offline */
    private String displayStatus;
}
