package com.hnist.client;

import com.hnist.domain.AiCompletionRequest;
import com.hnist.domain.AiCompletionResult;
import com.hnist.domain.AiMessage;

import java.util.List;
import java.util.function.Consumer;

/**
 * 大模型访问抽象：屏蔽具体服务商（DeepSeek 等），便于替换实现与单测打桩。
 * 所有调用在未配置或服务异常时抛 {@link com.hnist.common.exception.AiUnavailableException}，由上层决定降级文案。
 */
public interface AiModelClient {

    /** 简化入口：以 jsonMode 强制 JSON 输出，返回空内容时实现应抛异常而非返回空串。 */
    String complete(List<AiMessage> messages, boolean jsonMode, Integer userId);

    /** 标准补全：返回内容、tool_calls、finish_reason 与 token 用量，并按 scene 记录用量日志。 */
    AiCompletionResult complete(AiCompletionRequest request, Integer userId);

    /** 流式补全：每收到一段增量文本回调一次 onDelta，返回聚合后的完整结果。 */
    AiCompletionResult streamComplete(AiCompletionRequest request, Integer userId,
                                      Consumer<String> onDelta);

    /** API Key 等配置是否就绪；为 false 时调用方应直接跳过 AI 功能而非触发调用。 */
    boolean isConfigured();
}
