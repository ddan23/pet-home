package com.hnist.common.constant;

/** 管理端帖子展示状态，由 posts.status 与最新审核结论换算得出，不单独落库。 */
public enum AdminPostDisplayStatus {

    /** 已发布：status = normal，公开可见。 */
    PUBLISHED("published"),
    /** 待审核：status = pending，仅作者与管理员可见。 */
    PENDING("pending"),
    /** 审核拒绝：status = blocked 且最新人工结论为 rejected，区别于运营下架。 */
    REVIEW_REJECTED("rejected"),
    /** 已下架：非 normal/pending 且未被人审拒绝，含自动拦截与运营下架。 */
    OFFLINE("offline");

    private final String code;

    AdminPostDisplayStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 按 status 与最新审核结论换算展示状态，顺序即优先级：只有 blocked 需要 reviewStatus 再细分。 */
    public static AdminPostDisplayStatus resolve(String postStatus, String reviewStatus) {
        if (PostStatus.NORMAL.getCode().equals(postStatus)) {
            return PUBLISHED;
        }
        if (PostStatus.PENDING.getCode().equals(postStatus)) {
            return PENDING;
        }
        if (ModerationReviewStatus.REJECTED.getCode().equals(reviewStatus)) {
            return REVIEW_REJECTED;
        }
        return OFFLINE;
    }
}
