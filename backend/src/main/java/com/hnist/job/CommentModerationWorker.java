package com.hnist.job;

import com.hnist.support.CommentModerationPersistenceService;
import com.hnist.support.SensitiveWordFilter;

import com.hnist.domain.ModerationDecision;
import com.hnist.common.constant.ModerationStatus;
import com.hnist.service.ContentModerationService;
import com.hnist.common.constant.CommentStatus;
import com.hnist.mapper.PostCommentMapper;
import com.hnist.mapper.PostMapper;
import com.hnist.entity.Post;
import com.hnist.entity.PostComment;
import com.hnist.event.NotificationEvent;
import com.hnist.event.NotificationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 评论异步审核流程编排：由 CommentModerationListener 在评论落库事务提交后调用（非定时任务）。
 * 幂等性：只处理 status = pending 的评论，重复消费或人工已复核的评论直接跳过；
 * 并发安全：结论落库走 CommentModerationPersistenceService.applyDecision 的条件更新，
 * 抢不到 pending 的那次视为已被处理，不发通知、不重复计数。
 */
@Service
public class CommentModerationWorker {

    private static final String TRIGGER_SOURCE = "ASYNC";

    private final SensitiveWordFilter sensitiveWordFilter;
    private final ContentModerationService contentModerationService;
    private final PostCommentMapper commentMapper;
    private final PostMapper postMapper;
    private final CommentModerationPersistenceService persistenceService;
    private final NotificationEventPublisher notificationPublisher;

    public CommentModerationWorker(
            SensitiveWordFilter sensitiveWordFilter,
            ContentModerationService contentModerationService,
            PostCommentMapper commentMapper,
            PostMapper postMapper,
            CommentModerationPersistenceService persistenceService,
            NotificationEventPublisher notificationPublisher) {
        this.sensitiveWordFilter = sensitiveWordFilter;
        this.contentModerationService = contentModerationService;
        this.commentMapper = commentMapper;
        this.postMapper = postMapper;
        this.persistenceService = persistenceService;
        this.notificationPublisher = notificationPublisher;
    }

    /**
     * 审核主流程：命中高风险敏感词不调 AI 直接转人工（省一次模型调用且避免误杀）；
     * 其余先走 AI 审核，再叠加中风险敏感词的 REVIEW 结论。
     * changed = false 说明结论已被人抢先落库（人工复核或并发消费），必须终止，否则会对同一评论重复计数或重复通知。
     */
    public void moderate(Long commentId) {
        PostComment comment = commentMapper.selectById(commentId);
        if (comment == null || !CommentStatus.PENDING.getCode().equals(comment.getStatus())) {
            return;
        }
        long startedAt = System.currentTimeMillis();
        Optional<SensitiveWordFilter.SensitiveMatch> sensitiveMatch =
                sensitiveWordFilter.find(comment.getContent());
        ModerationDecision decision;
        if (sensitiveMatch.isPresent()
                && sensitiveMatch.get().severity() == ModerationStatus.BLOCK) {
            SensitiveWordFilter.SensitiveMatch match = sensitiveMatch.get();
            decision = new ModerationDecision(
                    ModerationStatus.REVIEW,
                    "命中高风险规则，需人工复核：" + match.category(),
                    "high", "[\"" + match.category() + "\"]",
                    "dfa", "sensitive-word-v1", null, null,
                    "{\"word\":\"" + match.word() + "\"}", 1.0D);
        } else {
            decision = contentModerationService.moderateComment(
                    comment.getSenderId(), comment.getContent());
            decision = mergeSensitiveReview(decision, sensitiveMatch.orElse(null));
        }
        long latency = System.currentTimeMillis() - startedAt;
        boolean changed = persistenceService.applyDecision(comment, decision,
                TRIGGER_SOURCE, sensitiveRuleJson(sensitiveMatch.orElse(null)), latency);
        if (!changed) {
            return;
        }
        if (decision.status() == ModerationStatus.ALLOW) {
            publishCommentCreated(comment);
        } else {
            publishAuthorResult(comment, decision.status());
        }
    }

    /** 审核链路异常时的降级入口：转 REVIEW 交人工，仍走 applyDecision 的条件更新，评论已非 pending 时静默跳过。 */
    public void fallbackToReview(Long commentId) {
        PostComment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            return;
        }
        ModerationDecision decision = ModerationDecision.review(
                "自动审核异常，内容正在人工复核");
        if (persistenceService.applyDecision(comment, decision, TRIGGER_SOURCE,
                null, 0L)) {
            publishAuthorResult(comment, ModerationStatus.REVIEW);
        }
    }

    private ModerationDecision mergeSensitiveReview(
            ModerationDecision decision, SensitiveWordFilter.SensitiveMatch match) {
        if (match == null || match.severity() != ModerationStatus.REVIEW
                || decision.status() != ModerationStatus.ALLOW) {
            return decision;
        }
        return new ModerationDecision(
                ModerationStatus.REVIEW, "命中需复核规则：" + match.category(),
                "medium", "[\"" + match.category() + "\"]",
                decision.modelName(), decision.promptVersion(), decision.imageUrl(),
                decision.imageHash(), "{\"word\":\"" + match.word() + "\"}",
                decision.confidence());
    }

    private String sensitiveRuleJson(SensitiveWordFilter.SensitiveMatch match) {
        if (match == null) {
            return null;
        }
        return "{\"word\":\"" + match.word()
                + "\",\"category\":\"" + match.category() + "\"}";
    }

    /** 仅在 AI 结论为 ALLOW 时通知帖子作者；自评不发；dedupeKey = comment-approved:{id} 保证重复投递只落一条。 */
    private void publishCommentCreated(PostComment comment) {
        Post post = postMapper.selectById(comment.getPostId());
        if (post == null || post.getUserId().equals(comment.getSenderId())) {
            return;
        }
        notificationPublisher.publish(new NotificationEvent(
                post.getUserId(), comment.getSenderId(), "community", "comment", "info",
                comment.getParentId() == null ? "帖子收到新评论" : "帖子收到新回复",
                comment.getContent(), "post", comment.getPostId().longValue(),
                "/community/" + comment.getPostId(),
                "comment-approved:" + comment.getId()));
    }

    /** 通知评论作者内容进入人工复核：作者不知道内容被拦会反复提交；dedupeKey 带 status，同一结论只发一次。 */
    private void publishAuthorResult(PostComment comment, ModerationStatus status) {
        notificationPublisher.publish(new NotificationEvent(
                comment.getSenderId(), null, "community",
                "moderation_pending", "info",
                "评论正在人工复核",
                "评论暂未公开展示，处理结果会通过站内消息通知。",
                "comment", comment.getId(), "/community/" + comment.getPostId(),
                "comment-moderation:" + comment.getId() + ":" + status.name()));
    }
}
