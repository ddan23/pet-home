package com.hnist.support;

import com.hnist.domain.ModerationDecision;
import com.hnist.common.constant.CommentStatus;
import com.hnist.mapper.PostCommentMapper;
import com.hnist.mapper.PostMapper;
import com.hnist.entity.PostComment;
import com.hnist.service.ModerationLogService;
import com.hnist.policy.CommentModerationPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 评论异步审核结果持久化。 */
@Service
public class CommentModerationPersistenceService {

    private final PostCommentMapper commentMapper;
    private final PostMapper postMapper;
    private final ModerationLogService moderationLogService;

    public CommentModerationPersistenceService(PostCommentMapper commentMapper,
                                               PostMapper postMapper,
                                               ModerationLogService moderationLogService) {
        this.commentMapper = commentMapper;
        this.postMapper = postMapper;
        this.moderationLogService = moderationLogService;
    }

    /**
     * 幂等应用评论审核结果：仅当评论仍处于 pending 状态才生效，已处理的直接返回 false。
     * 审核日志先落库；状态变更走条件更新 updateStatusIfCurrent（仅 pending→目标状态），
     * 并发重复回调只有一次能更新成功，且仅在实际通过时帖子评论数才 +1。整体在事务内，异常全部回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean applyDecision(PostComment comment, ModerationDecision decision,
                                 String triggerSource, String ruleHits, long latencyMs) {
        PostComment currentComment = commentMapper.selectById(comment.getId());
        if (currentComment == null || !CommentStatus.PENDING.getCode()
                .equals(currentComment.getStatus())) {
            return false;
        }
        moderationLogService.record(comment.getSenderId(), "COMMENT", comment.getId(),
                decision, triggerSource, ruleHits, excerpt(comment.getContent()), latencyMs);
        String approvedStatus = CommentModerationPolicy.approvedStatus(decision.status());
        if (approvedStatus != null) {
            int updated = commentMapper.updateStatusIfCurrent(
                    comment.getId(), CommentStatus.PENDING.getCode(),
                    approvedStatus);
            if (updated > 0) {
                postMapper.updateCommentsCount(comment.getPostId(), 1);
            }
            return updated > 0;
        }
        return true;
    }

    private String excerpt(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= 1000 ? content : content.substring(0, 1000);
    }
}
