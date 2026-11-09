package com.hnist.client.impl;

import com.hnist.client.AiModelClient;
import com.hnist.common.exception.AiUnavailableException;
import com.hnist.config.AiProperties;
import com.hnist.domain.AiCompletionRequest;
import com.hnist.domain.AiCompletionResult;
import com.hnist.domain.AiMessage;
import com.hnist.domain.AiToolCall;
import com.hnist.mapper.AiUsageLogMapper;
import com.hnist.entity.AiUsageLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

/**
 * 基于 Spring AI 官方 DeepSeek starter 的模型客户端：阻塞与流式都委托官方 DeepSeekChatModel，
 * 本类只负责三件事：领域消息与 Spring AI Message 互转、用量落库、异常统一转 {@link AiUnavailableException}。
 * 限流（AiRateLimitService）、会话存储、审核双通道仍在业务层，Spring AI 只承接「调模型」这一层。
 */
@Slf4j
@Component
public class SpringAiModelClient implements AiModelClient {

    private static final String ERROR_MESSAGE = "AI 助手暂时不可用，请稍后重试";

    /** 与 spring.ai.deepseek.api-key 同源：配置就绪判断以此为准，避免从 chatModel 内部反解。 */
    private final Environment environment;

    private final AiProperties properties;
    private final DeepSeekChatModel chatModel;
    private final AiUsageLogMapper usageLogMapper;

    public SpringAiModelClient(DeepSeekChatModel chatModel, AiProperties properties,
                               AiUsageLogMapper usageLogMapper, Environment environment) {
        this.chatModel = chatModel;
        this.properties = properties;
        this.usageLogMapper = usageLogMapper;
        this.environment = environment;
    }

    /** 兼容旧签名的简化入口：空内容视为失败抛 {@link AiUnavailableException}，返回前统一 trim。 */
    @Override
    public String complete(List<AiMessage> messages, boolean jsonMode, Integer userId) {
        AiCompletionResult result = complete(new AiCompletionRequest(
                messages, "legacy", jsonMode, List.of(), null, null), userId);
        if (result.content() == null || result.content().isBlank()) {
            throw new AiUnavailableException("AI 未返回有效内容");
        }
        return result.content().trim();
    }

    /**
     * 阻塞式补全：总开关关闭或 API Key 缺失时直接抛 {@link AiUnavailableException}；
     * 生成参数为请求值与 spring.ai.deepseek.chat.options 默认值的合成，maxTokens 再与业务上限取较小者。
     */
    @Override
    public AiCompletionResult complete(AiCompletionRequest request, Integer userId) {
        if (!isConfigured()) {
            throw new AiUnavailableException("DeepSeek API 尚未配置");
        }
        long startedAt = System.nanoTime();
        Prompt prompt = new Prompt(toSpringMessages(request.messages()), buildOptions(request));
        try {
            ChatResponse response = chatModel.call(prompt);
            String content = response == null || response.getResult() == null
                    ? null : response.getResult().getOutput().getText();
            List<AiToolCall> toolCalls = List.of();
            if (content == null || content.isBlank()) {
                throw new AiUnavailableException("AI 未返回有效内容");
            }
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
            AiCompletionResult result = new AiCompletionResult(
                    content.trim(),
                    toolCalls,
                    response.getResult().getMetadata() == null
                            ? null : response.getResult().getMetadata().getFinishReason(),
                    usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                    usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens(),
                    Duration.ofNanos(System.nanoTime() - startedAt).toMillis(),
                    response.getMetadata() == null ? null : response.getMetadata().getModel());
            recordUsage(userId, request.scene(), result, true, null);
            return result;
        } catch (AiUnavailableException exception) {
            recordUsage(userId, request.scene(), null, false, "AI_UNAVAILABLE");
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("调用DeepSeek失败", exception);
            recordUsage(userId, request.scene(), null, false, "PROVIDER_ERROR");
            throw new AiUnavailableException(ERROR_MESSAGE, exception);
        }
    }

    /** 配置就绪 = 业务开关打开且 DeepSeek API Key 非空白；两者任一缺失都按未配置处理。 */
    @Override
    public boolean isConfigured() {
        String apiKey = environment.getProperty("spring.ai.deepseek.api-key");
        return properties.isEnabled() && apiKey != null && !apiKey.isBlank();
    }

    /**
     * 流式补全：官方 stream() 返回增量 ChatResponse 流，这里逐片回调 onDelta 并聚合完整结果，
     * 用 Flux.toIterable 拉取以贴合现有同步契约（SSE 线程池内执行）；流中无任何有效文本视为失败。
     */
    @Override
    public AiCompletionResult streamComplete(AiCompletionRequest request, Integer userId,
                                             Consumer<String> onDelta) {
        if (!isConfigured()) {
            throw new AiUnavailableException("DeepSeek API 尚未配置");
        }
        long startedAt = System.nanoTime();
        Prompt prompt = new Prompt(toSpringMessages(request.messages()), buildOptions(request));
        StringBuilder content = new StringBuilder();
        int[] tokens = new int[2];
        String[] finishReason = {null};
        try {
            chatModel.stream(prompt).doOnNext(response -> {
                if (response.getResult() != null && response.getResult().getOutput() != null) {
                    String text = response.getResult().getOutput().getText();
                    if (text != null && !text.isEmpty()) {
                        content.append(text);
                        onDelta.accept(text);
                    }
                    if (response.getResult().getMetadata() != null
                            && response.getResult().getMetadata().getFinishReason() != null) {
                        finishReason[0] = response.getResult().getMetadata().getFinishReason();
                    }
                }
                Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
                if (usage != null) {
                    tokens[0] = usage.getPromptTokens() == null ? tokens[0] : usage.getPromptTokens();
                    tokens[1] = usage.getCompletionTokens() == null ? tokens[1] : usage.getCompletionTokens();
                }
            }).toIterable().forEach(response -> { /* 消费由 doOnNext 完成 */ });
        } catch (RuntimeException exception) {
            log.warn("流式调用DeepSeek失败", exception);
            recordUsage(userId, request.scene(), null, false, "PROVIDER_ERROR");
            throw new AiUnavailableException(ERROR_MESSAGE, exception);
        }
        if (content.length() == 0) {
            recordUsage(userId, request.scene(), null, false, "AI_UNAVAILABLE");
            throw new AiUnavailableException("AI 未返回有效内容");
        }
        AiCompletionResult result = new AiCompletionResult(content.toString(), List.of(),
                finishReason[0], tokens[0], tokens[1],
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), null);
        recordUsage(userId, request.scene(), result, true, null);
        return result;
    }

    /** 生成参数合成：maxTokens 与业务上限取较小者；jsonMode 用官方 response_format 强制 JSON 输出。 */
    private DeepSeekChatOptions buildOptions(AiCompletionRequest request) {
        DeepSeekChatOptions.Builder builder = DeepSeekChatOptions.builder()
                .maxTokens(Math.min(request.maxTokens(), properties.getMaxTokens()))
                .temperature(request.temperature());
        if (request.jsonMode()) {
            builder.responseFormat(ResponseFormat.builder()
                    .type(ResponseFormat.Type.JSON_OBJECT)
                    .build());
        }
        return builder.build();
    }

    /**
     * 领域消息 → Spring AI Message：纯文本走 System/User/Assistant 三种官方消息；
     * 多模态（文本 + image_url 内容块）转官方 UserMessage + Media（data URL 解出字节）。
     * tool 角色当前没有调用方，出现即视为非法请求直接失败，不做有损降级。
     */
    private List<Message> toSpringMessages(List<AiMessage> messages) {
        List<Message> converted = new ArrayList<>(messages.size());
        for (AiMessage message : messages) {
            switch (message.getRole() == null ? "" : message.getRole()) {
                case "system" -> converted.add(new SystemMessage(message.textContent()));
                case "user" -> converted.add(toUserMessage(message));
                case "assistant" -> converted.add(new AssistantMessage(message.textContent()));
                default -> throw new AiUnavailableException(
                        "不支持的消息角色：" + message.getRole());
            }
        }
        return converted;
    }

    private UserMessage toUserMessage(AiMessage message) {
        if (message.getContent() instanceof String text) {
            return new UserMessage(text);
        }
        // 多模态内容块：提取文本块与图片块，图片 data URL 解出 MIME 与字节交给官方 Media
        String text = "";
        List<org.springframework.ai.content.Media> mediaList = new ArrayList<>();
        if (message.getContent() instanceof List<?> blocks) {
            for (Object blockObj : blocks) {
                if (!(blockObj instanceof java.util.Map<?, ?> block)) {
                    continue;
                }
                if ("text".equals(block.get("type"))) {
                    text = String.valueOf(block.get("text"));
                } else if ("image_url".equals(block.get("type"))
                        && block.get("image_url") instanceof java.util.Map<?, ?> image
                        && image.get("url") instanceof String dataUrl) {
                    mediaList.add(toMedia(dataUrl));
                }
            }
        }
        return mediaList.isEmpty() ? new UserMessage(text)
                : UserMessage.builder().text(text).media(mediaList).build();
    }

    /** 解析 data:image/xxx;base64,yyyy 形式的 data URL：审计图片由前端转 base64 上传，不含外链。 */
    private Media toMedia(String dataUrl) {
        int commaIndex = dataUrl.indexOf(',');
        String header = dataUrl.substring(0, commaIndex);
        String mime = header.substring(header.indexOf(':') + 1, header.indexOf(';'));
        byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(commaIndex + 1));
        return Media.builder().mimeType(MimeTypeUtils.parseMimeType(mime)).data(bytes).build();
    }

    /** 落库 AI 用量日志：任何写入异常只告警吞掉，绝不让统计问题影响 AI 调用主流程。 */
    private void recordUsage(Integer userId, String scene, AiCompletionResult result,
                             boolean success, String errorCode) {
        try {
            AiUsageLog usageLog = new AiUsageLog();
            usageLog.setUserId(userId);
            usageLog.setScene(scene);
            // 失败路径没有响应体可取模型名，回落到默认配置的模型，保证日志里场景与模型始终可对应
            usageLog.setModelName(result == null || result.modelName() == null
                    ? defaultModelName() : result.modelName());
            usageLog.setPromptTokens(result == null ? 0 : result.promptTokens());
            usageLog.setCompletionTokens(result == null ? 0 : result.completionTokens());
            usageLog.setTotalTokens(result == null ? 0
                    : result.promptTokens() + result.completionTokens());
            usageLog.setLatencyMs(result == null ? 0 : result.latencyMs());
            usageLog.setSuccess(success);
            usageLog.setErrorCode(errorCode);
            usageLogMapper.insert(usageLog);
        } catch (RuntimeException exception) {
            log.warn("记录AI用量日志失败，场景：{}", scene, exception);
        }
    }

    /** 默认模型名从官方配置读取：与响应体里的模型名口径一致，失败日志也能定位用的哪个模型。 */
    private String defaultModelName() {
        String configured = environment.getProperty("spring.ai.deepseek.chat.options.model");
        return configured == null || configured.isBlank() ? "deepseek" : configured;
    }
}
