package com.hnist.service.impl;

import com.hnist.domain.AiMessage;
import com.hnist.client.AiModelClient;
import com.hnist.domain.AiCompletionRequest;
import com.hnist.domain.AiCompletionResult;
import com.hnist.domain.ModerationDecision;
import com.hnist.common.constant.ModerationStatus;
import com.hnist.service.AiImageContentService;
import com.hnist.support.AiJsonParser;
import com.hnist.service.ContentModerationService;
import com.hnist.common.exception.AiUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;

/** 调用大模型判定社区帖子/评论（含图片）能否直接公开。 */
@Slf4j
@Service
public class ContentModerationServiceImpl implements ContentModerationService {

    // 提示词每改一次口径就要升版本：版本号会写入 content_moderation_logs.prompt_version，用于回溯历史判定依据。
    private static final String PROMPT_VERSION = "moderation-v3";
    private static final int MODERATION_MAX_TOKENS = 1200;
    // 审核结论要可复现，采样温度压到 0：同一段内容重复送审不应得到不同结果。
    private static final double MODERATION_TEMPERATURE = 0.0D;
    // 提示词只给模型 pass/review 两档、不授予拒绝权：未通过的内容统一交人工，避免模型误杀直接挡掉用户内容。
    private static final String MODERATION_PROMPT = """
            你是宠物社区的严格内容审核员。审核用户准备发布的帖子或评论。
            必须阻止：虐待动物、血腥暴力、违法野生动物交易、诈骗、色情、个人隐私泄露、
            辱骂骚扰、垃圾广告、刷单引流、危险医疗建议、无资质售药。
            普通晒宠、求助、领养讨论、健康经验分享和正常商品讨论应允许。
            同时检查文字和图片；图片中若出现虐待、血腥、违法交易、二维码广告、联系方式泄露等也要标记。
            只输出 JSON，不要 Markdown：
            {"decision":"pass|review","riskLevel":"none|low|medium|high",
             "categories":["分类"],"reason":"简短中文原因",
             "evidence":[{"source":"text|image","detail":"证据摘要"}],
             "confidence":0到1之间的小数}
            pass 只用于明确正常的内容；review 用于任何违规、高风险、含义不清或需要人工确认的内容。
            不得输出最终拒绝决定，所有未通过内容统一交由人工复审。
            """;

    private final AiModelClient modelClient;
    private final AiJsonParser jsonParser;
    private final AiImageContentService imageContentService;
    private final Environment environment;

    public ContentModerationServiceImpl(AiModelClient modelClient, AiJsonParser jsonParser,
                                        AiImageContentService imageContentService,
                                        Environment environment) {
        this.modelClient = modelClient;
        this.jsonParser = jsonParser;
        this.imageContentService = imageContentService;
        this.environment = environment;
    }

    /** 审核日志的模型名与用量日志同源：都从 Spring AI 官方配置节读取当前生效模型。 */
    private String modelName() {
        String configured = environment.getProperty("spring.ai.deepseek.chat.options.model");
        return configured == null || configured.isBlank() ? "deepseek" : configured;
    }

    @Override
    public ModerationDecision moderatePost(Integer userId, String title, String content,
                                           String imageUrl) {
        // 这里只负责 AI 判定：本地敏感词命中与“取更严一侧”的合并由调用方 PostServiceImpl 完成，本类不重复查敏感词表。
        return moderate(userId, "帖子", title, content, imageUrl);
    }

    @Override
    public ModerationDecision moderateComment(Integer userId, String content) {
        return moderate(userId, "评论", null, content, null);
    }

    private ModerationDecision moderate(Integer userId, String targetType, String title,
                                        String content, String imageUrl) {
        // 审核能力不可用时不能默认放行：直接转 REVIEW 交人工，宁可延迟公开也不让未审内容先上线。
        if (!modelClient.isConfigured()) {
            log.warn("DeepSeek未配置，内容转人工复核");
            return new ModerationDecision(ModerationStatus.REVIEW,
                    "自动审核暂不可用，内容正在人工复核", "medium",
                    null, modelName(), PROMPT_VERSION,
                    imageUrl, null, null, null);
        }
        String postText = "内容类型：" + targetType
                + "\n标题：" + safeText(title)
                + "\n正文：" + safeText(content);
        AiImageContentService.ImageContent image = imageContentService.load(imageUrl);
        // 文字干净不代表图片干净：图片读不到就等于没审图，所以这里统一转人工并标记 IMAGE_REVIEW_REQUIRED，让复核人知道“没审的是图”。
        if (imageUrl != null && !imageUrl.isBlank() && image == null) {
            return new ModerationDecision(ModerationStatus.REVIEW,
                    "图片无法自动读取，内容正在人工复核", "medium",
                    "[\"IMAGE_REVIEW_REQUIRED\"]", modelName(), PROMPT_VERSION,
                    imageUrl, null, null, null);
        }
        // 审核失败一律不降级为放行：模型超时、返回残缺 JSON 或任何未预期异常都落到下面的 REVIEW 分支。
        // 两个 catch 的日志只写异常本身，不拼 postText 与 imageUrl——正文是用户隐私，图片地址可能带签名参数，都不该进日志。
        try {
            // 有图才拼多模态消息，图片按 low 细节下发以压低单次请求的 token 与延迟；纯文字内容不构造图片段。
            AiMessage userMessage = image == null
                    ? new AiMessage("user", postText)
                    : AiMessage.multimodal("user", postText, image.dataUrl(), "low");
            AiCompletionResult completion = modelClient.complete(new AiCompletionRequest(List.of(
                    new AiMessage("system", MODERATION_PROMPT),
                    userMessage
            ), "community_moderation", true, List.of(),
                    MODERATION_MAX_TOKENS, MODERATION_TEMPERATURE), userId);
            JsonNode node = jsonParser.parse(completion.content());
            // 字段缺失一律按最保守值兜底（review / medium / 0.5）：模型返回残缺 JSON 时不能因为解析默认值而放行。
            String decision = node.path("decision").asText("review");
            String reason = node.path("reason").asText("内容需要人工复核");
            String riskLevel = node.path("riskLevel").asText("medium");
            String categories = node.path("categories").toString();
            String evidence = node.path("evidence").toString();
            // 置信度夹到 0~1，防止模型给出越界数值直接落到 content_moderation_logs.confidence。
            double confidence = Math.max(0.0D, Math.min(1.0D,
                    node.path("confidence").asDouble(0.5D)));
            boolean explicitlyPassed = "pass".equalsIgnoreCase(decision)
                    || "allow".equalsIgnoreCase(decision);
            boolean lowRisk = "none".equalsIgnoreCase(riskLevel)
                    || "low".equalsIgnoreCase(riskLevel);
            // 自动放行必须同时满足“模型明确 pass”和“低风险”，任一不满足都转人工，避免把含义不清的内容直接公开。
            if (explicitlyPassed && lowRisk) {
                return new ModerationDecision(ModerationStatus.ALLOW, reason, riskLevel,
                        categories, modelName(), PROMPT_VERSION,
                        imageUrl, image == null ? null : image.hash(), evidence, confidence);
            }
            return new ModerationDecision(ModerationStatus.REVIEW, reason, riskLevel,
                    categories, modelName(), PROMPT_VERSION,
                    imageUrl, image == null ? null : image.hash(), evidence, confidence);
        } catch (AiUnavailableException exception) {
            log.warn("社区内容AI审核不可用，转人工复核", exception);
            return new ModerationDecision(ModerationStatus.REVIEW,
                    "自动审核暂不可用，内容正在人工复核", "medium",
                    null, modelName(), PROMPT_VERSION,
                    imageUrl, image == null ? null : image.hash(), null, null);
        } catch (RuntimeException exception) {
            log.warn("社区多模态审核异常，转人工复核", exception);
            return new ModerationDecision(ModerationStatus.REVIEW,
                    "自动审核结果异常，内容正在人工复核", "medium",
                    null, modelName(), PROMPT_VERSION,
                    imageUrl, image == null ? null : image.hash(), null, null);
        }
    }

    private String safeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        // 超长正文只送前 2000 字：控住上下文成本，同时保证 null 与空白在拼提示词时不出现 "null" 字样。
        return text.length() > 2000 ? text.substring(0, 2000) : text;
    }
}
