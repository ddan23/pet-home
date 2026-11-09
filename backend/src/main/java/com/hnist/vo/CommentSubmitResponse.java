package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 评论提交结果：评论先以 pending 落库再异步审核，因此成功响应不代表已经公开。 */
@Data
@AllArgsConstructor
public class CommentSubmitResponse {

    private Long commentId;

    /** 固定返回 auditing：提交时审核尚未发生，不能回 normal 让前端误以为已通过。 */
    private String status;

    private String message;
}
