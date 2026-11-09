package com.hnist.common.constant;

/** 对外统一的审核状态，屏蔽帖子与评论各自的状态字段差异；只有 approved 视为公开。 */
public enum ModerationState {

    /** 审核通过：对应帖子/评论的 normal，其他用户可见。 */
    APPROVED("approved"),
    /** 待审核：对应帖子/评论的 pending，仅作者与管理员可见。 */
    PENDING("pending"),
    /** 审核拒绝：对应帖子/评论的 blocked，列表查询时过滤。 */
    REJECTED("rejected");

    private final String code;

    ModerationState(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 对外可见性判定：只有 approved 算公开，pending 与 rejected 都不进公开列表。 */
    public boolean isPublic() {
        return this == APPROVED;
    }

    /** 帖子状态换算：无法识别的值一律归为 pending，宁可先当待审也不误判为通过。 */
    public static ModerationState fromPostStatus(String status) {
        if (PostStatus.NORMAL.getCode().equals(status)) {
            return APPROVED;
        }
        if (PostStatus.BLOCKED.getCode().equals(status)) {
            return REJECTED;
        }
        return PENDING;
    }

    /** 评论状态换算：规则与帖子一致，未知值落 pending。 */
    public static ModerationState fromCommentStatus(String status) {
        if (CommentStatus.NORMAL.getCode().equals(status)) {
            return APPROVED;
        }
        if (CommentStatus.BLOCKED.getCode().equals(status)) {
            return REJECTED;
        }
        return PENDING;
    }

    public String toPostStatus() {
        return switch (this) {
            case APPROVED -> PostStatus.NORMAL.getCode();
            case PENDING -> PostStatus.PENDING.getCode();
            case REJECTED -> PostStatus.BLOCKED.getCode();
        };
    }

    public String toCommentStatus() {
        return switch (this) {
            case APPROVED -> CommentStatus.NORMAL.getCode();
            case PENDING -> CommentStatus.PENDING.getCode();
            case REJECTED -> CommentStatus.BLOCKED.getCode();
        };
    }
}
