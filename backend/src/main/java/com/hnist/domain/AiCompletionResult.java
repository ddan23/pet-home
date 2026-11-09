package com.hnist.domain;

import java.util.List;

/** 模型响应及用量信息，用量用于配额统计与成本核算。 */
public record AiCompletionResult(
        String content,
        List<AiToolCall> toolCalls,
        /** 结束原因，原样透传模型返回的 finish_reason，本项目不做归一化。 */
        String finishReason,
        int promptTokens,
        int completionTokens,
        /** 单次调用耗时，单位毫秒。 */
        long latencyMs,
        String modelName
) {

    public AiCompletionResult {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
