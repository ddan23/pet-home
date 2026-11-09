package com.hnist.domain;

import java.util.List;

/** 统一的模型请求：所有调用方都经此进入模型客户端，由紧凑构造器统一兜底默认参数。 */
public record AiCompletionRequest(
        List<AiMessage> messages,
        /** 调用场景标签，随用量记录一起落库用于归集；空白时归一为 general。 */
        String scene,
        /** true 时要求模型返回 JSON，调用方仍需容错解析失败。 */
        boolean jsonMode,
        List<AiToolDefinition> tools,
        /** 输出 token 上限，null 或 ≤ 0 时取 2048。 */
        Integer maxTokens,
        /** 采样温度，null 时取 0.2；需要可复现的分类/抽取场景由调用方显式传 0.0。 */
        Double temperature
) {

    public AiCompletionRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        scene = scene == null || scene.isBlank() ? "general" : scene;
        maxTokens = maxTokens == null || maxTokens <= 0 ? 2048 : maxTokens;
        temperature = temperature == null ? 0.2D : temperature;
    }

    public AiCompletionRequest(List<AiMessage> messages, String scene,
                               boolean jsonMode, List<AiToolDefinition> tools) {
        this(messages, scene, jsonMode, tools, null, null);
    }
}
