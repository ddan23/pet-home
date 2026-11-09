package com.hnist.common.constant;

/** 帖子状态，落 posts.status；只有 normal 进入公开信息流。 */
public enum PostStatus {

    /** 正常：自动审核放行或人工通过后写入，公开可见。 */
    NORMAL("normal"),
    /** 待审核：审核结论为复核时写入，仅作者与管理员可见。 */
    PENDING("pending"),
    /** 已屏蔽：人工拒绝或运营下架后写入，列表查询过滤。 */
    BLOCKED("blocked");

    private final String code;

    PostStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
