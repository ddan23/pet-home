package com.hnist.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** post_comments 表行映射：帖子评论，parentId 与 rootId 表达楼中楼关系。 */
@Data
public class PostComment implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Integer postId;
    private Integer senderId;
    /** 直接父评论ID，顶级评论为 null */
    private Long parentId;
    /** 所属根评论ID，用于按楼聚合 */
    private Long rootId;
    private String content;
    /** normal、pending、blocked（CommentStatus） */
    private String status;
    private Integer likeCount;
    private LocalDateTime createdAt;
    /** 非DB字段，关联 users 带出的评论人昵称 */
    private String senderName;
    /** 非DB字段，评论人头像 */
    private String senderAvatar;

    /** 非DB字段，管理端评论审核列表展示用 */
    private String postTitle;

    /** 非DB字段，帖子作者ID */
    private Integer postUserId;
}
