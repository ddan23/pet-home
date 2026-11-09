package com.hnist.event;

/**
 * 评论提交后触发的异步审核事件。
 * 只携带 commentId：审核所需的其余数据由 CommentModerationWorker 自行查询最新状态，
 * 避免事件持有过期快照，也保证重复消费时能读到评论当前的真实状态。
 */
public record CommentModerationEvent(Long commentId) {
}
