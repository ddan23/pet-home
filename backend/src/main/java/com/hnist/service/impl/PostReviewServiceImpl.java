package com.hnist.service.impl;

import com.hnist.common.constant.ModerationReviewStatus;
import com.hnist.common.constant.PostStatus;
import com.hnist.entity.ContentModerationLog;
import com.hnist.entity.Post;
import com.hnist.service.AuditLogService;
import com.hnist.service.ModerationLogService;
import com.hnist.service.PostReviewService;
import com.hnist.service.PostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** 帖子人工审核：更新帖子状态，并按流转类型决定是否回写审核结论与审计日志。 */
@Service
public class PostReviewServiceImpl implements PostReviewService {

    private final PostService postService;
    private final ModerationLogService moderationLogService;
    private final AuditLogService auditLogService;

    public PostReviewServiceImpl(PostService postService,
                                 ModerationLogService moderationLogService,
                                 AuditLogService auditLogService) {
        this.postService = postService;
        this.moderationLogService = moderationLogService;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewPost(Integer postId, String status, Integer operatorId) {
        // 先在变更前取状态快照：动作名（通过/拒绝/下架/恢复）和是否回写审核结论都按变更前判定，updatePostStatus 之后就读不到了。
        Post post = postService.getPostById(postId);
        String previousStatus = post == null ? null : post.getStatus();
        ContentModerationLog latest = moderationLogService.findLatest(
                "POST", postId.longValue());
        // blocked 有两种成因（人工拒绝 / 管理员下架）：只有最新日志是 rejected，blocked→normal 才能认定为“复核通过”，否则只是“恢复上架”。
        boolean wasRejected = latest != null
                && ModerationReviewStatus.REJECTED.getCode().equals(latest.getReviewStatus());
        // 状态白名单、流转校验与 CAS 更新都在 PostService.updatePostStatus 内完成，这里只做编排，不维护第二套状态规则。
        postService.updatePostStatus(postId, status);
        // 审核结论并非每次流转都要改写：只有待审帖出结论、或被拒帖复核通过时才动 content_moderation_logs，下架/恢复只改 posts.status。
        if (shouldReviewLog(previousStatus, status, wasRejected)) {
            // 结论只落 approved/rejected；updateReviewStatus 的 SQL 限定 review_status='pending'，重复提交不会覆盖第一次结论。
            moderationLogService.review("POST", postId.longValue(), operatorId,
                    PostStatus.NORMAL.getCode().equals(status)
                            ? ModerationReviewStatus.APPROVED.getCode()
                            : ModerationReviewStatus.REJECTED.getCode());
        }
        // 审计与审核日志分工不同：审核日志只留最终结论，审计要求每次操作都有痕迹，所以 shouldReviewLog 为 false 时这里照样要写。
        // 详情里带变更前状态快照：帖子记录已被更新，事后只能靠 fromStatus/toStatus 还原操作轨迹。
        auditLogService.record(operatorId,
                resolvePostStatusAction(previousStatus, status, wasRejected),
                "POST", postId.longValue(),
                Map.of("fromStatus", String.valueOf(previousStatus),
                        "toStatus", status));
    }

    // 判定是否需要回写审核结论：待审帖出结论，或被拒帖复核通过；其余流转（下架、恢复上架）不改写历史审核结论，避免日志被覆盖。
    private boolean shouldReviewLog(String previousStatus, String currentStatus,
                                    boolean wasRejected) {
        return PostStatus.PENDING.getCode().equals(previousStatus)
                || (PostStatus.BLOCKED.getCode().equals(previousStatus)
                && PostStatus.NORMAL.getCode().equals(currentStatus)
                && wasRejected);
    }

    // 同一对前后状态可能对应两种业务动作：blocked→normal 按 wasRejected 拆成 POST_APPROVE（复核通过）和 POST_RESTORE（恢复上架），否则审计和统计分不开。
    private String resolvePostStatusAction(String previousStatus, String currentStatus,
                                           boolean wasRejected) {
        if (PostStatus.PENDING.getCode().equals(previousStatus)
                && PostStatus.NORMAL.getCode().equals(currentStatus)) {
            return "POST_APPROVE";
        }
        if (PostStatus.PENDING.getCode().equals(previousStatus)
                && PostStatus.BLOCKED.getCode().equals(currentStatus)) {
            return "POST_REJECT";
        }
        if (PostStatus.NORMAL.getCode().equals(previousStatus)
                && PostStatus.BLOCKED.getCode().equals(currentStatus)) {
            return "POST_OFFLINE";
        }
        if (PostStatus.BLOCKED.getCode().equals(previousStatus)
                && PostStatus.NORMAL.getCode().equals(currentStatus)) {
            return wasRejected ? "POST_APPROVE" : "POST_RESTORE";
        }
        // 匹配不到标准流转时统一记 POST_STATUS：审计动作字段不能为空，否则后台按动作筛选会漏记录。
        return "POST_STATUS";
    }
}
