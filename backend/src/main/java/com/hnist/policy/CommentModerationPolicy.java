package com.hnist.policy;

import com.hnist.common.constant.CommentStatus;
import com.hnist.common.constant.ModerationReviewStatus;
import com.hnist.common.constant.ModerationStatus;

/** 评论审核状态规则：评论状态与审核日志结论的映射集中在这里，供异步审核与管理端人工复核共用。 */
public final class CommentModerationPolicy {

    private CommentModerationPolicy() {
    }

    /** 评论全生命周期只有 normal/pending/blocked 三种状态，管理端筛选与展示都以此白名单为准。 */
    public static boolean isSupportedStatus(String status) {
        return CommentStatus.NORMAL.getCode().equals(status)
                || CommentStatus.PENDING.getCode().equals(status)
                || CommentStatus.BLOCKED.getCode().equals(status);
    }

    /** 人工复核只接受终态 normal/blocked：放行 pending 会把评论重新混进待审队列，审核结论永远无法收敛。 */
    public static boolean isSupportedReviewResult(String status) {
        return CommentStatus.NORMAL.getCode().equals(status)
                || CommentStatus.BLOCKED.getCode().equals(status);
    }

    /** 审核日志结论映射：normal 记 approved，其余（只可能是 blocked）记 rejected，与 reviewComment 的裁决口径一致。 */
    public static String reviewLogStatus(String status) {
        return CommentStatus.NORMAL.getCode().equals(status)
                ? ModerationReviewStatus.APPROVED.getCode()
                : ModerationReviewStatus.REJECTED.getCode();
    }

    /** 只有 ALLOW 映射为 normal，其余（REVIEW/BLOCK）返回 null：调用方 applyDecision 以 null 区分「保持 pending 不改状态」与「条件更新为 normal 并计数」。 */
    public static String approvedStatus(ModerationStatus decisionStatus) {
        return decisionStatus == ModerationStatus.ALLOW
                ? CommentStatus.NORMAL.getCode() : null;
    }
}
