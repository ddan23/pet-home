package com.hnist.policy;

import com.hnist.common.constant.PostStatus;
import com.hnist.domain.ModerationDecision;
import com.hnist.common.constant.ModerationStatus;

/** 帖子审核状态规则：初始状态落点与状态机流转白名单集中在这里，发布流程与管理端审核共用。 */
public final class PostModerationPolicy {

    private PostModerationPolicy() {
    }

    /** 只有 ALLOW 直接落 normal 对外可见；REVIEW 与 BLOCK 一律先落 pending 等人工复核，自动审核不直接判定拒绝，避免误杀。 */
    public static String initialStatus(ModerationDecision decision) {
        return decision.status() == ModerationStatus.ALLOW
                ? PostStatus.NORMAL.getCode() : PostStatus.PENDING.getCode();
    }

    /** 帖子只有 normal/pending/blocked 三种状态，管理端改状态前先过这道白名单。 */
    public static boolean isSupportedStatus(String status) {
        return PostStatus.NORMAL.getCode().equals(status)
                || PostStatus.PENDING.getCode().equals(status)
                || PostStatus.BLOCKED.getCode().equals(status);
    }

    /**
     * 只允许三组流转：pending→normal/blocked（审核出结论）、normal→blocked（下架）、blocked→normal（恢复）。
     * 放行其他组合（如 pending→pending、normal→pending）会让待审帖脱离审核队列，或把已裁决帖子改回待审反复占用人工。
     */
    public static boolean canTransition(String previousStatus, String targetStatus) {
        return isPendingToFinal(previousStatus, targetStatus)
                || isNormalToBlocked(previousStatus, targetStatus)
                || isBlockedToNormal(previousStatus, targetStatus);
    }

    private static boolean isPendingToFinal(String previousStatus, String targetStatus) {
        return PostStatus.PENDING.getCode().equals(previousStatus)
                && (PostStatus.NORMAL.getCode().equals(targetStatus)
                || PostStatus.BLOCKED.getCode().equals(targetStatus));
    }

    private static boolean isNormalToBlocked(String previousStatus, String targetStatus) {
        return PostStatus.NORMAL.getCode().equals(previousStatus)
                && PostStatus.BLOCKED.getCode().equals(targetStatus);
    }

    private static boolean isBlockedToNormal(String previousStatus, String targetStatus) {
        return PostStatus.BLOCKED.getCode().equals(previousStatus)
                && PostStatus.NORMAL.getCode().equals(targetStatus);
    }
}
