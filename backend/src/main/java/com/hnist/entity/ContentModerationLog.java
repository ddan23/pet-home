package com.hnist.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** content_moderation_logs 表行映射：每次内容审核的结论留痕，供人工复核与后台统计。 */
@Data
public class ContentModerationLog {

    private Long id;
    private String targetType;
    private Long targetId;
    private Integer userId;
    /** 触发来源，未指定时为 AI */
    private String triggerSource;
    /** DFA 命中结果 */
    private String ruleHits;
    private String targetExcerpt;
    /** 审核耗时，单位毫秒 */
    private Long latencyMs;
    /** 机器结论：ALLOW、REVIEW、BLOCK */
    private String aiDecision;
    private String riskLevel;
    private String categories;
    private String imageUrl;
    /** 图片内容哈希，char(64)，复核时用于确认是否为同一张图 */
    private String imageHash;
    /** 判定证据 JSON */
    private String evidenceJson;
    /** 置信度，decimal(4,3) */
    private Double confidence;
    private String reason;
    private String modelName;
    private String promptVersion;
    private Integer reviewerId;
    /** 人工复核进度：pending、approved、rejected、auto_completed */
    private String reviewStatus;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
