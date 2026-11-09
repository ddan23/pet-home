package com.hnist.service.impl;

import com.hnist.service.CareRecommendationService;
import com.hnist.service.PetHealthQueryService;

import com.hnist.service.HealthRecommendationService;

import com.hnist.support.HealthScopeGuard;
import com.hnist.support.PetHealthContextAssembler;
import com.hnist.domain.AiCompletionRequest;
import com.hnist.domain.AiMessage;
import com.hnist.client.AiModelClient;
import com.hnist.domain.HealthScopeDecision;
import com.hnist.support.AiRateLimitService;
import com.hnist.support.AiJsonParser;
import com.hnist.common.exception.AiUnavailableException;
import com.hnist.domain.AiAdvisorResult;
import com.hnist.domain.AiRecommendationIntent;
import com.hnist.vo.CareRecommendationResponse;
import com.hnist.domain.HealthChatMessage;
import com.hnist.entity.Pet;
import com.hnist.entity.PetAllergy;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** 结合宠物档案与平台在售清单的 AI 商品与服务推荐。 */
@Service
public class HealthRecommendationServiceImpl implements HealthRecommendationService {

    // 免责声明随每条结果返回（含拒答分支）：推荐文案不能读成治疗承诺或绝对低敏保证。
    private static final String DISCLAIMER =
            "推荐仅用于辅助选择，商品健康标签不表示疾病治疗或绝对低敏保证。";
    private static final String TYPE_RECOMMENDATION = "RECOMMENDATION";
    private static final int MAX_RECOMMENDATIONS = 5;
    // 单条商品聚合出的健康标签上限：标签只用于卡片摘要，超过 8 个会挤掉名称与价格。
    private static final int MAX_HEALTH_TAGS = 8;
    // 意图解析只输出结构化条件并禁止输出商品名、链接：解析结果只用于查库，模型不能借这一步凭空产出候选。
    private static final String INTENT_PROMPT = """
            你是宠物商品和服务推荐需求解析器。只处理猫狗商品或宠物服务需求。
            根据当前问题和最近对话提取结构化条件，只输出 JSON：
            {"itemType":"GOODS|SERVICE|ALL","intent":"用户核心需求",
             "healthTags":["最多8个标签"],"limit":1到5}
            不要输出商品名称、链接、诊断或药物剂量。
            """;
    // 生成阶段把候选列表定为唯一事实来源：价格、库存、低敏结论都必须能在候选里找到，模型只负责解释筛选依据。
    private static final String ANSWER_PROMPT = """
            你是“猫狗宠物之家”的养宠推荐助手，只介绍下方候选列表中的真实商品和服务。
            不得编造商品、价格、库存、店铺、链接、医疗功效或绝对低敏保证。
            不得诊断疾病或推荐处方药剂量。
            请用简洁中文说明筛选依据、推荐理由和需要用户自行核对的信息，
            并提示用户结合宠物过敏史和商品成分确认。
            """;

    private final AiModelClient modelClient;
    private final HealthScopeGuard scopeGuard;
    private final AiRateLimitService rateLimitService;
    private final CareRecommendationService careRecommendationService;
    private final PetHealthQueryService petHealthQueryService;
    private final PetHealthContextAssembler contextAssembler;
    private final AiJsonParser jsonParser;

    public HealthRecommendationServiceImpl(AiModelClient modelClient,
                                       HealthScopeGuard scopeGuard,
                                       AiRateLimitService rateLimitService,
                                       CareRecommendationService careRecommendationService,
                                       PetHealthQueryService petHealthQueryService,
                                       PetHealthContextAssembler contextAssembler,
                                       AiJsonParser jsonParser) {
        this.modelClient = modelClient;
        this.scopeGuard = scopeGuard;
        this.rateLimitService = rateLimitService;
        this.careRecommendationService = careRecommendationService;
        this.petHealthQueryService = petHealthQueryService;
        this.contextAssembler = contextAssembler;
        this.jsonParser = jsonParser;
    }

    /** 非流式入口，等价于 onDelta 传 null：整段回答生成完再返回，限流与越界拦截语义一致。 */
    @Override
    public AiAdvisorResult recommend(Integer userId, Pet pet, String message,
                                     List<HealthChatMessage> history) {
        return recommend(userId, pet, message, history, null);
    }

    /** onDelta 非 null 时边生成边回调增量文本，null 时整体返回；两条入口走同一套限流、越界拦截与候选过滤。 */
    @Override
    public AiAdvisorResult recommendStream(Integer userId, Pet pet, String message,
                                           List<HealthChatMessage> history,
                                           Consumer<String> onDelta) {
        return recommend(userId, pet, message, history, onDelta);
    }

    private AiAdvisorResult recommend(Integer userId, Pet pet, String message,
                                      List<HealthChatMessage> history,
                                      Consumer<String> onDelta) {
        // 模型未配置时抛 AiUnavailableException 由 GlobalExceptionHandler 转成 503：推荐没有纯规则兜底文案，宁可直接告知不可用。
        if (!modelClient.isConfigured()) {
            throw new AiUnavailableException("DeepSeek API 尚未配置，请联系管理员");
        }
        // 限流必须放在第一次模型调用之前：一次推荐串行调用分类、意图解析、生成三次，超限请求不能在消耗额度后才被拒。
        rateLimitService.check(userId);
        // 工具模式（classifyAgentQuestion）才允许讨论平台内商品与服务；越界直接返回拒答结果，靠 refused 字段告知前端，不抛异常。
        HealthScopeDecision scope =
                scopeGuard.classifyAgentQuestion(message, history);
        if (!scope.allowed()) {
            return new AiAdvisorResult(
                    "我只能根据猫狗档案推荐平台内的宠物商品或养护服务，请描述您的具体需求。",
                    true, scope.emergency(), scope.category(), DISCLAIMER,
                    TYPE_RECOMMENDATION, List.of(), List.of());
        }
        // 紧急信号优先于推荐：主题虽然合法也先给急诊提示，避免用户拿商品推荐处理需要立即就医的情况。
        if (scope.emergency()) {
            return new AiAdvisorResult(
                    "当前描述包含紧急危险信号，请立即联系急诊兽医，暂不建议通过商品推荐处理。",
                    true, true, "EMERGENCY", DISCLAIMER,
                    TYPE_RECOMMENDATION, List.of(), List.of());
        }

        AiRecommendationIntent intent = extractIntent(userId, message, history);
        List<CareRecommendationResponse> candidates =
                careRecommendationService.recommend(
                        pet, intent.getIntent(), intent.getItemType(),
                        intent.getHealthTags(), intent.getLimit());
        // 候选为空时逐级放宽：先去健康标签，再把 itemType 放宽到 ALL。空结果是平台供给覆盖不足而非调用失败，
        // 抛错只会让用户白重试；放宽后仍由 CareRecommendationService 按 pet 的物种和生命阶段过滤，不会推出错物种的条目。
        if (candidates.isEmpty() && !intent.getHealthTags().isEmpty()) {
            candidates = careRecommendationService.recommend(
                    pet, intent.getIntent(), intent.getItemType(), List.of(),
                    intent.getLimit());
        }
        if (candidates.isEmpty() && !"ALL".equals(intent.getItemType())) {
            candidates = careRecommendationService.recommend(
                    pet, intent.getIntent(), "ALL", List.of(), intent.getLimit());
        }
        List<CareRecommendationResponse> safeCandidates =
                filterAllergens(pet.getId(), candidates);
        // 全部候选被过敏原过滤掉时返回 NO_MATCH：没有真实候选还让模型写推荐，等于放任它编造商品。
        if (safeCandidates.isEmpty()) {
            return new AiAdvisorResult(
                    "当前平台内没有同时满足宠物情况、关键词和过敏信息的在售商品或服务。"
                            + "可以补充预算、商品类型或服务项目后继续查询。",
                    false, false, "NO_MATCH", DISCLAIMER,
                    TYPE_RECOMMENDATION, List.of(), List.of());
        }

        String answer = explain(userId, pet, message, history, safeCandidates, onDelta);
        return new AiAdvisorResult(
                answer, false, false, "PET_PRODUCT_SERVICE", DISCLAIMER,
                TYPE_RECOMMENDATION, List.of(), safeCandidates);
    }

    private AiRecommendationIntent extractIntent(Integer userId, String message,
                                                  List<HealthChatMessage> history) {
        // 意图解析用 temperature 0.0 的独立调用：条件抽取要可复现，温度随机会让同一句话每次查出不同候选。
        List<AiMessage> messages = new ArrayList<>();
        messages.add(new AiMessage("system", INTENT_PROMPT));
        appendHistory(messages, history);
        messages.add(new AiMessage("user", message));
        String result = modelClient.complete(new AiCompletionRequest(
                messages, "recommendation_intent", true, List.of(),
                500, 0.0D), userId).content();
        JsonNode node = jsonParser.parse(result);
        AiRecommendationIntent intent = new AiRecommendationIntent();
        String itemType = node.path("itemType").asText("ALL");
        // itemType 只认 GOODS/SERVICE，脏值退回 ALL；limit 是模型输出，钳制到 1..MAX_RECOMMENDATIONS 防止一次返回过量条目。
        if (!"GOODS".equals(itemType) && !"SERVICE".equals(itemType)) {
            itemType = "ALL";
        }
        intent.setItemType(itemType);
        intent.setIntent(node.path("intent").asText(message));
        intent.setHealthTags(readTags(node.path("healthTags")));
        int limit = node.path("limit").asInt(MAX_RECOMMENDATIONS);
        intent.setLimit(Math.min(Math.max(limit, 1), MAX_RECOMMENDATIONS));
        return intent;
    }

    private String explain(Integer userId, Pet pet, String message,
                           List<HealthChatMessage> history,
                           List<CareRecommendationResponse> candidates,
                           Consumer<String> onDelta) {
        // 提示词里只放 assemble(pet) 组装的宠物档案和已过滤候选：PetHealthContext 不含账号、联系方式与订单，用户隐私不随请求外发。
        List<AiMessage> messages = new ArrayList<>();
        messages.add(new AiMessage("system", ANSWER_PROMPT));
        appendHistory(messages, history);
        messages.add(new AiMessage("user",
                "当前宠物档案：\n" + contextAssembler.toJson(
                        contextAssembler.assemble(pet))
                        + "\n\n用户需求：\n" + message
                        + "\n\n真实候选列表：\n" + contextAssembler.toJson(candidates)));
        AiCompletionRequest request = new AiCompletionRequest(
                messages, "health_recommendation", false, List.of(), 1000, 0.2D);
        if (onDelta == null) {
            return modelClient.complete(request, userId).content().trim();
        }
        return modelClient.streamComplete(request, userId, onDelta).content().trim();
    }

    private List<CareRecommendationResponse> filterAllergens(
            Integer petId, List<CareRecommendationResponse> candidates) {
        // 过敏原过滤在候选交给模型之前完成：命中过敏原的条目直接剔除，不能寄希望于模型在文案里替用户把关。
        Set<String> allergies = new LinkedHashSet<>();
        for (PetAllergy allergy : petHealthQueryService.listAllergies(petId)) {
            if (allergy.getAllergenCode() != null && !allergy.getAllergenCode().isBlank()) {
                allergies.add(allergy.getAllergenCode().trim().toLowerCase(Locale.ROOT));
            }
        }
        if (allergies.isEmpty()) {
            return candidates;
        }
        return candidates.stream()
                .filter(candidate -> !containsAllergen(candidate, allergies))
                .toList();
    }

    private boolean containsAllergen(CareRecommendationResponse candidate,
                                     Set<String> allergies) {
        // 过敏原 code 与名称、类目、健康标签拼串后做子串匹配：health_tags 是自由文本，只能做保守包含判断，宁可多剔除也不漏放。
        String content = String.join(" ",
                value(candidate.getName()),
                value(candidate.getCategoryName()),
                String.join(" ", candidate.getHealthTags() == null
                        ? List.of() : candidate.getHealthTags()))
                .toLowerCase(Locale.ROOT);
        return allergies.stream().anyMatch(content::contains);
    }

    private List<String> readTags(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            // 标签截断到 8 个，与 INTENT_PROMPT 的约定一致：多出来的标签会放大查询条件，把候选筛空。
            if (!value.isEmpty() && tags.size() < MAX_HEALTH_TAGS) {
                tags.add(value);
            }
        }
        return tags;
    }

    private void appendHistory(List<AiMessage> messages, List<HealthChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return;
        }
        // 只带最近 12 条历史：上下文过长会把候选列表挤出窗口，也会放大历史消息里的提示词注入面。
        int start = Math.max(0, history.size() - 12);
        for (int index = start; index < history.size(); index++) {
            HealthChatMessage item = history.get(index);
            messages.add(new AiMessage(
                    "assistant".equalsIgnoreCase(item.getRole()) ? "assistant" : "user",
                    item.getContent()));
        }
    }

    private String value(String text) {
        return text == null ? "" : text;
    }
}
