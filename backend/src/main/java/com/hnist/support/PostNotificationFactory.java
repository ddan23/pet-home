package com.hnist.support;

import com.hnist.common.constant.ModerationReviewStatus;
import com.hnist.common.constant.PostStatus;
import com.hnist.entity.ContentModerationLog;
import com.hnist.entity.Post;
import com.hnist.entity.PostComment;
import com.hnist.entity.User;
import com.hnist.common.constant.ModerationStatus;
import com.hnist.event.NotificationEvent;

import java.time.LocalDate;

/**
 * 社区帖子相关通知事件构造器：统一生成幂等键（type:业务ID[:操作者:日期]），
 * 使消费方对同一事件天然去重，重复触发不会重复推送。
 */
public final class PostNotificationFactory {

    private PostNotificationFactory() {
    }

    /** 幂等键含操作者与当天日期：同一用户同一天对同一帖子的重复点赞只通知一次。 */
    public static NotificationEvent like(Post post, User user, Integer operatorId) {
        String nickname = user.getNickname() == null ? user.getUsername() : user.getNickname();
        String title = post.getTitle() == null ? "帖子" : post.getTitle();
        return new NotificationEvent(
                post.getUserId(), operatorId, "community", "like", "info",
                "帖子收到新的点赞", nickname + " 点赞了你的帖子《" + title + "》",
                "post", post.getId().longValue(), "/community/" + post.getId(),
                "like:" + post.getId() + ":" + operatorId + ":" + LocalDate.now());
    }

    public static NotificationEvent commentApproved(Post post, PostComment comment) {
        return new NotificationEvent(
                post.getUserId(), comment.getSenderId(), "community",
                "comment", "info", "帖子收到新评论", comment.getContent(),
                "post", post.getId().longValue(), "/community/" + post.getId(),
                "comment-approved:" + comment.getId());
    }

    public static NotificationEvent commentRejected(
            Post post, PostComment comment, Integer operatorId) {
        return new NotificationEvent(
                comment.getSenderId(), operatorId, "community",
                "moderation_blocked", "warning", "评论未通过审核",
                "评论未通过人工复核，已停止公开展示。",
                "comment", comment.getId(), "/community/" + post.getId(),
                "comment-review:" + comment.getId());
    }

    public static NotificationEvent moderationPending(
            Post post, ModerationStatus status) {
        return new NotificationEvent(
                post.getUserId(), null, "community",
                "moderation_pending", "info",
                "帖子正在人工复核",
                "帖子暂未公开展示，处理结果会通过站内消息通知。",
                "post", post.getId().longValue(), "/community/" + post.getId(),
                "post-moderation:" + post.getId() + ":" + status.name());
    }

    /** 按帖子状态迁移（pending→normal / pending→blocked / normal→blocked / 恢复）决定通知类型与文案。 */
    public static NotificationEvent postTransition(
            Post post, String previousStatus, String currentStatus,
            ContentModerationLog latest) {
        String type;
        String level;
        String title;
        String content;
        if (PostStatus.PENDING.getCode().equals(previousStatus)
                && PostStatus.NORMAL.getCode().equals(currentStatus)) {
            type = "moderation_passed";
            level = "info";
            title = "帖子审核通过";
            content = "你的帖子已通过人工复核并公开。";
        } else if (PostStatus.PENDING.getCode().equals(previousStatus)
                && PostStatus.BLOCKED.getCode().equals(currentStatus)) {
            type = "moderation_blocked";
            level = "warning";
            title = "帖子未通过审核";
            content = "你的帖子未通过人工复核，暂不公开展示。";
        } else if (PostStatus.NORMAL.getCode().equals(previousStatus)
                && PostStatus.BLOCKED.getCode().equals(currentStatus)) {
            type = "post_offline";
            level = "warning";
            title = "帖子已下架";
            content = "你的帖子已由管理员停止公开展示。";
        } else {
            boolean reviewRejected = latest != null
                    && ModerationReviewStatus.REJECTED.getCode()
                    .equals(latest.getReviewStatus());
            type = reviewRejected ? "moderation_passed" : "post_restored";
            level = "info";
            title = reviewRejected ? "帖子复核通过" : "帖子已恢复上架";
            content = reviewRejected
                    ? "你的帖子经复核后已恢复公开。"
                    : "你的帖子已恢复公开展示。";
        }
        return new NotificationEvent(
                post.getUserId(), null, "community", type, level, title, content,
                "post", post.getId().longValue(), "/community/" + post.getId(),
                "post-transition:" + post.getId() + ":" + previousStatus + ":" + currentStatus);
    }
}
