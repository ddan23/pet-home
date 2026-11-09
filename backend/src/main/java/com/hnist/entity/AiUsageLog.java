package com.hnist.entity;

import lombok.Data;

/** ai_usage_logs 表行映射：AI 调用用量日志，不保存用户原始健康问题。 */
@Data
public class AiUsageLog {

    private Long id;

    private Integer userId;

    private String scene;

    private String modelName;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    /** 调用耗时，单位毫秒 */
    private Long latencyMs;

    private Boolean success;

    private String errorCode;
}
