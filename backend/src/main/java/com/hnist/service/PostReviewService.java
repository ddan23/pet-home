package com.hnist.service;

/** 帖子人工审核的状态流转、审核结论回写与审计留痕，三者同一事务。 */
public interface PostReviewService {

    /**
     * 状态白名单与流转规则由 PostService.updatePostStatus 判定，非法状态或并发改写抛 BusinessException 并整体回滚。
     * 只有待审帖出结论或被拒帖复核通过才回写 content_moderation_logs，审计日志则每次操作都写。
     */
    void reviewPost(Integer postId, String status, Integer operatorId);
}
