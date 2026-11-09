package com.hnist.mq.message;

/**
 * 评论审核消息体：只携带评论 id，审核所需的其余数据由消费者查询数据库最新状态，
 * 避免消息持有过期快照，重复消费时也能读到评论当前的真实状态。
 *
 * @param commentId 待审核评论 id
 */
public record CommentModerationMessage(Long commentId) {
}
