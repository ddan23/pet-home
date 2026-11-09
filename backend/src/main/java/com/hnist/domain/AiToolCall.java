package com.hnist.domain;

/**
 * 模型返回的工具调用。
 * id 用于与发起调用配对，回传结果时必须原样带回；arguments 是模型给出的 JSON 字符串，不做结构校验。
 */
public record AiToolCall(String id, String name, String arguments) {
}
