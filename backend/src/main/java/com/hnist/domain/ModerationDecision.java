package com.hnist.domain;

import com.hnist.common.constant.ModerationStatus;

/**
 * 内容审核结果：由审核服务判定后经 ModerationLogServiceImpl 落审核日志，status 决定帖子/评论是否公开。
 * riskLevel 取值 none、low、medium、high；confidence 在规则兜底或模型未返回时为 null，不能当 0 处理。
 */
public record ModerationDecision(ModerationStatus status, String reason, String riskLevel,
                                 String categories, String modelName, String promptVersion,
                                 String imageUrl, String imageHash, String evidence,
                                 Double confidence) {

    public ModerationDecision(ModerationStatus status, String reason) {
        this(status, reason, "low", null, "deepseek-flash",
                "moderation-v3", null, null, null, null);
    }

    public ModerationDecision(ModerationStatus status, String reason, String riskLevel,
                              String categories, String modelName, String promptVersion) {
        this(status, reason, riskLevel, categories, modelName, promptVersion,
                null, null, null, null);
    }

    public static ModerationDecision allow() {
        return new ModerationDecision(ModerationStatus.ALLOW, "内容审核通过",
                "none", null, "deepseek-flash", "moderation-v3",
                null, null, null, 1.0D);
    }

    public static ModerationDecision review(String reason) {
        return new ModerationDecision(ModerationStatus.REVIEW, reason,
                "medium", null, "deepseek-flash", "moderation-v3");
    }

    public static ModerationDecision block(String reason) {
        return new ModerationDecision(ModerationStatus.BLOCK, reason,
                "high", null, "deepseek-flash", "moderation-v1");
    }
}
