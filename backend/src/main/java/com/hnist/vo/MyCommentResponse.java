package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 我的评论列表项：联表带出所属帖子标题与作者，便于作者在原帖上下文中查看处理进度。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MyCommentResponse {

    private Long id;

    private Integer postId;

    private String postTitle;

    private String postAuthorName;

    private String content;

    /** 不筛状态：pending 与被拒的评论同样返回，是否公开由前端按该字段渲染。 */
    private String status;

    private LocalDateTime createdAt;
}
