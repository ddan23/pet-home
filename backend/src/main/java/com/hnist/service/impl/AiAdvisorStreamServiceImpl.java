package com.hnist.service.impl;

import com.hnist.service.AiConversationService;
import com.hnist.service.HealthAssistantService;
import com.hnist.service.HealthRecommendationService;
import com.hnist.service.HealthReportService;
import com.hnist.service.PetHealthQueryService;

import com.hnist.service.AiAdvisorStreamService;

import com.hnist.support.PetHealthContextAssembler;
import com.hnist.support.AiStreamEventWriter;
import com.hnist.common.constant.AiChatMode;
import com.hnist.common.exception.AiUnavailableException;
import com.hnist.common.exception.BusinessException;
import com.hnist.domain.AiAdvisorResult;
import com.hnist.vo.AiHealthChatResponse;
import com.hnist.dto.AiStreamChatRequest;
import com.hnist.dto.AiStreamRecommendationRequest;
import com.hnist.dto.AiStreamReportRequest;
import com.hnist.domain.HealthChatMessage;
import com.hnist.entity.AiChatMessage;
import com.hnist.entity.AiChatSession;
import com.hnist.entity.Pet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** AI 养宠顾问流式响应编排。 */
@Slf4j
@Service
public class AiAdvisorStreamServiceImpl implements AiAdvisorStreamService {

    // 与 spring.mvc.async.request-timeout 保持同一口径：超时后 emitter 主动 complete，连接不会挂到容器默认值上。
    private static final long STREAM_TIMEOUT_MILLIS = 120_000L;
    // 报告模式没有用户输入，用固定文案充当 user 消息落库，会话记录里才不会出现只有回答没有提问的断层。
    private static final String REPORT_USER_MESSAGE = "生成当前宠物的专属健康报告";

    private final AiConversationService conversationService;
    private final PetHealthQueryService petHealthQueryService;
    private final PetHealthContextAssembler contextAssembler;
    private final HealthAssistantService healthAssistantService;
    private final HealthReportService healthReportService;
    private final HealthRecommendationService healthRecommendationService;
    private final AiStreamEventWriter eventWriter;
    private final Executor streamExecutor;

    public AiAdvisorStreamServiceImpl(
            AiConversationService conversationService,
            PetHealthQueryService petHealthQueryService,
            PetHealthContextAssembler contextAssembler,
            HealthAssistantService healthAssistantService,
            HealthReportService healthReportService,
            HealthRecommendationService healthRecommendationService,
            AiStreamEventWriter eventWriter,
            @Qualifier("aiStreamExecutor") Executor streamExecutor) {
        this.conversationService = conversationService;
        this.petHealthQueryService = petHealthQueryService;
        this.contextAssembler = contextAssembler;
        this.healthAssistantService = healthAssistantService;
        this.healthReportService = healthReportService;
        this.healthRecommendationService = healthRecommendationService;
        this.eventWriter = eventWriter;
        this.streamExecutor = streamExecutor;
    }

    /**
     * 问答流式入口。SSE 契约：连接 120 秒超时或客户端中途断连时由 emitter 回调 complete 释放，
     * 模型失败以流内 error 事件下发而非 HTTP 错误码（响应头在异步开始时已按 200 发出）；下同。
     */
    @Override
    public SseEmitter streamChat(Integer userId, AiStreamChatRequest request) {
        return stream(userId, request.getConversationId(), request.getPetId(),
                AiChatMode.QA, request.getMessage());
    }

    /** 健康报告流式入口：不推送增量 delta，整篇生成后由 emitAnswer 按 10 字一片补发；SSE 契约同 {@link #streamChat}。 */
    @Override
    public SseEmitter streamReport(Integer userId, AiStreamReportRequest request) {
        return stream(userId, request.getConversationId(), request.getPetId(),
                AiChatMode.REPORT, REPORT_USER_MESSAGE);
    }

    /** 推荐流式入口：意图解析与候选查询同步完成后边生成边推 delta；SSE 契约同 {@link #streamChat}。 */
    @Override
    public SseEmitter streamRecommendation(
            Integer userId, AiStreamRecommendationRequest request) {
        return stream(userId, request.getConversationId(), request.getPetId(),
                AiChatMode.RECOMMENDATION, request.getMessage());
    }

    private SseEmitter stream(Integer userId, Long conversationId, Integer petId,
                              String mode, String userMessage) {
        // 接口必须立即返回 SseEmitter 让容器进入异步模式，生成放到 aiStreamExecutor：模型调用耗时几十秒，留在 Web 线程会拖垮线程池。
        SseEmitter emitter = createEmitter();
        streamExecutor.execute(() -> execute(
                emitter, userId, conversationId, petId, mode, userMessage));
        return emitter;
    }

    private void execute(SseEmitter emitter, Integer userId, Long requestedConversationId,
                         Integer petId, String mode, String userMessage) {
        AiChatSession session = null;
        // 只有用户消息真正落库后才补 FAILED 助手消息，否则会给一个还没开始的提问配上错误回答。
        boolean userMessageSaved = false;
        try {
            // 会话归属、宠物归属、模式一致性全部在这里收口：任何一项不匹配都抛 404，后续读写都基于这个 session。
            session = conversationService.resolve(
                    userId, requestedConversationId, petId, mode);
            Pet pet = requirePet(userId, petId);
            // 首个事件先给出 conversationId，前端据此把后续 delta 绑定到对应会话，新建会话时也能立刻拿到 id。
            eventWriter.send(emitter, "start", Map.of(
                    "conversationId", session.getId(),
                    "messageType", mode,
                    "petName", safeText(pet.getName())
            ));
            // 历史必须在本条用户消息落库前取出：否则刚写入的这条会被再拼一次进提示词，模型看到重复提问。
            List<HealthChatMessage> history =
                    conversationService.loadHistory(session.getId());
            AiChatMessage savedUserMessage = conversationService.appendUserMessage(
                    session, userMessage, "TEXT");
            userMessageSaved = true;
            StringBuilder streamedText = new StringBuilder();
            // 报告模式不传 onDelta：generate 一次性返回结构化结果，正文稍后由 emitAnswer 分片补发，问答与推荐才边生成边推 delta。
            AiAdvisorResult result = generate(
                    userId, pet, mode, savedUserMessage, history,
                    AiChatMode.REPORT.equals(mode) ? null
                            : delta -> appendDelta(emitter, streamedText, delta));
            // 助手消息等完整结果再落库：中途失败不会留下半截回答，失败记录统一由 handleFailure 写 FAILED。
            AiChatMessage assistantMessage =
                    conversationService.appendAssistantMessage(session, result);
            // 引用与推荐为空时不发事件，前端不必为每种模式都处理空数组分支。
            if (!result.getCitations().isEmpty()) {
                eventWriter.send(emitter, "citations",
                        Map.of("citations", result.getCitations()));
            }
            if (!result.getRecommendations().isEmpty()) {
                eventWriter.send(emitter, "recommendations",
                        Map.of("recommendations", result.getRecommendations()));
            }
            emitAnswer(emitter, streamedText, result);
            // done 携带 messageId 与 refused/emergency：前端靠它决定是否展示免责声明、跳转急诊提示，也是本轮唯一收尾信号。
            eventWriter.send(emitter, "done", Map.of(
                    "conversationId", session.getId(),
                    "messageId", assistantMessage.getId(),
                    "messageType", result.getMessageType(),
                    "refused", result.isRefused(),
                    "emergency", result.isEmergency(),
                    "category", safeText(result.getCategory()),
                    "disclaimer", safeText(result.getDisclaimer())
            ));
            // 成功路径必须显式 complete 关闭 SSE，否则连接要挂到 120s 超时才释放。
            emitter.complete();
        } catch (BusinessException | AiUnavailableException exception) {
            // 业务与模型不可用异常转成流内 error 事件：HTTP 响应头早已按 200 发出，改成 5xx 会让前端只看到断流而不是可读原因。
            handleFailure(emitter, session, userMessageSaved, exception.getMessage());
        } catch (IOException exception) {
            // 客户端已断开，再 send 必然失败；只做 complete 释放连接，也不再补写 FAILED，避免用户重连后看到未发生的错误占位。
            log.debug("AI流式连接已关闭", exception);
            emitter.complete();
        } catch (UncheckedIOException exception) {
            // delta 通道把 IOException 包成了运行时异常，语义与上一条相同：连接断了就安静收尾。
            log.debug("AI流式输出中断，客户端可能已断开", exception);
            emitter.complete();
        } catch (RuntimeException exception) {
            // 兜底异常只回固定文案，堆栈只进日志：异常信息可能带内部路径或提示词内容。
            log.error("AI流式响应失败，模式：{}", mode, exception);
            handleFailure(emitter, session, userMessageSaved, "AI服务暂时不可用，请稍后重试");
        }
    }

    private AiAdvisorResult generate(Integer userId, Pet pet, String mode,
                                     AiChatMessage userMessage,
                                     List<HealthChatMessage> history,
                                     Consumer<String> onDelta) {
        // 三种模式共用同一套落库与事件流程，模式只决定调用哪个生成服务以及是否支持增量输出；模式在 resolve 已校验，default 仅兜底。
        // 限流与用量记录都在下游生成服务和模型客户端里完成（调用模型前 check，超限抛 429），本层不重复计数，429 文案沿 BusinessException 分支转成 error 事件。
        switch (mode) {
            case AiChatMode.QA:
                // 问答的主题拦截、证据检索与引用校验都在 healthAssistantService 内部完成，拒答同样按正常结果返回，这里不再判一次。
                AiHealthChatResponse response = healthAssistantService.chatStream(
                        userId, contextAssembler.assemble(pet),
                        userMessage.getContent(), history, onDelta);
                return new AiAdvisorResult(
                        response.getAnswer(), response.getRefused(), response.getEmergency(),
                        response.getCategory(), response.getDisclaimer(), "TEXT",
                        response.getCitations(), List.of());
            case AiChatMode.REPORT:
                return healthReportService.generate(userId, pet, history);
            case AiChatMode.RECOMMENDATION:
                return healthRecommendationService.recommendStream(
                        userId, pet, userMessage.getContent(), history, onDelta);
            default:
                throw new BusinessException("AI会话模式不合法");
        }
    }

    private void appendDelta(SseEmitter emitter, StringBuilder buffer, String delta) {
        // 缓存与推送写在同一处：最后要用累积文本和权威答案比对，决定是否补一条 replace。
        buffer.append(delta);
        try {
            eventWriter.send(emitter, "delta", Map.of("text", delta));
        } catch (IOException exception) {
            // Consumer<String> 签名不接受受检异常，包成 UncheckedIOException 交给 execute 的 catch 统一收尾。
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * 落定最终答案：安全校验与拒答替换发生在流式输出之后，已推送的 delta 不能直接当作终稿。
     */
    private void emitAnswer(SseEmitter emitter, StringBuilder streamedText,
                            AiAdvisorResult result) throws IOException {
        String answer = result.getAnswer() == null ? "" : result.getAnswer().trim();
        // 没有增量输出（报告模式）时按 10 字一片补发 delta，前端只需实现一种追加逻辑。
        if (streamedText.length() == 0) {
            eventWriter.streamText(emitter, answer);
            return;
        }
        // 与已推送内容一致就不重发，避免前端把同一段文字追加两遍。
        if (answer.equals(streamedText.toString().trim())) {
            return;
        }
        // 不一致说明答案被安全策略改写（越界、缺引用、证据不足），必须下发 replace 让界面丢弃已渲染的流式内容。
        eventWriter.send(emitter, "replace", Map.of(
                "text", answer,
                "category", safeText(result.getCategory())));
    }

    private void handleFailure(SseEmitter emitter, AiChatSession session,
                               boolean userMessageSaved, String message) {
        String safeMessage = message == null || message.isBlank()
                ? "AI服务暂时不可用，请稍后重试" : message;
        // 失败也要在会话里留痕：补一条 FAILED 助手消息，loadHistory 会跳过它，但用户能在历史列表看到这次没答上来。
        if (session != null && userMessageSaved) {
            try {
                conversationService.appendFailedMessage(session, safeMessage);
            } catch (RuntimeException exception) {
                // 补记录失败不能掩盖原始错误：只记日志，error 事件照常下发。
                log.warn("记录AI失败消息失败", exception);
            }
        }
        try {
            // error 事件必须在 complete 之前发出，complete 之后 emitter 已关闭，再发会直接抛异常。
            eventWriter.send(emitter, "error", Map.of("message", safeMessage));
            emitter.complete();
        } catch (IOException | IllegalStateException exception) {
            // 连接已失效时用 completeWithError 结束异步请求，否则容器会一直等这个 emitter 超时。
            log.debug("AI错误事件发送失败", exception);
            emitter.completeWithError(exception);
        }
    }

    // 宠物归属校验：查询条件带 userId，越权访问返回 404 而不是 403，避免暴露他人宠物是否存在。
    private Pet requirePet(Integer userId, Integer petId) {
        Pet pet = petHealthQueryService.findOwnedPet(userId, petId);
        if (pet == null) {
            throw new BusinessException(404, "宠物不存在或无权访问");
        }
        return pet;
    }

    private SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        // 超时必须显式 complete：SseEmitter 不释放就一直在异步请求表里，连接与线程都回收不掉。
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> log.debug("AI SSE连接异常", error));
        return emitter;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
