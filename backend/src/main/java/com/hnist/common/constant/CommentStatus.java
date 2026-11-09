package com.hnist.common.constant;

/** 评论状态，落 comments.status；新评论不直接公开，需审核结论落在正常状态才展示。 */
public enum CommentStatus {

    /** 正常：自动审核放行或人工通过后写入，只有该状态对外可见。 */
    NORMAL("normal"),
    /** 待审核：新评论的初始状态，仅作者与管理员看得到。 */
    PENDING("pending"),
    /** 已屏蔽：审核拒绝或运营下架后写入，列表查询按该值过滤。 */
    BLOCKED("blocked");

    private final String code;

    CommentStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
