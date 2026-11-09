package com.hnist.common.constant;

/** 审核任务的人工处理状态，落审核日志 review_status；记录一次判定是否已有人工结论。 */
public enum ModerationReviewStatus {

    /** 待人工：AI 判定为 review 时写入，后台待办列表按该值取数。 */
    PENDING("pending"),
    /** 人工通过：管理员放行后写入，被审内容同时转为正常状态。 */
    APPROVED("approved"),
    /** 人工驳回：管理员拒绝后写入，被审内容同时转为屏蔽状态。 */
    REJECTED("rejected"),
    /** 自动完成：AI 直接给出 allow，无需人工介入，日志据此口径统计。 */
    AUTO_COMPLETED("auto_completed");

    private final String code;

    ModerationReviewStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
