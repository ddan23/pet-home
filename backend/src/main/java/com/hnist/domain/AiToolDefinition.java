package com.hnist.domain;

import java.util.Map;

/** OpenAI 兼容格式的工具定义：只描述能力与参数 schema，实际执行由调用方按 name 分发。 */
public record AiToolDefinition(String name, String description,
                               /** JSON Schema 结构，原样透传给模型，不做校验。 */
                               Map<String, Object> parameters) {

    /** 按 function calling 的嵌套结构包装，直接拼进请求体的 tools 字段。 */
    public Map<String, Object> toApiValue() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", parameters
                )
        );
    }
}
