package com.hnist.service.impl;

import com.hnist.service.CareRecommendationService;

import com.hnist.vo.CareRecommendationResponse;
import com.hnist.mapper.ProductMapper;
import com.hnist.entity.Pet;
import com.hnist.entity.Product;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 基于宠物档案和商品养护属性的推荐实现。 */
@Service
public class CareRecommendationServiceImpl implements CareRecommendationService {

    private static final int MAX_RESULTS = 5;
    // 月龄分档边界与 resolveLifeStage 的语义一一对应：满 12 个月转成年，满 96 个月（8 岁）转老年。
    private static final int YOUNG_MAX_MONTHS = 12;
    private static final int SENIOR_MIN_MONTHS = 96;
    // 候选池固定取最近创建的 50 条在售条目：评分在内存里做，池子必须有上界，代价是更早上架的商品不会进入本轮候选。
    private static final int CANDIDATE_LIMIT = 50;
    private static final String TYPE_ALL = "ALL";
    private static final String TYPE_GOODS = "GOODS";
    private static final String TYPE_SERVICE = "SERVICE";
    // 停用词剔除“推荐/宠物/购买”这类泛词：否则几乎所有商品描述都能命中关键词，分数失去区分度。
    private static final Set<String> STOP_WORDS = Set.of(
            "推荐", "想要", "可以", "帮我", "适合", "宠物", "猫狗",
            "商品", "服务", "购买", "预约", "有没有", "什么");

    private final ProductMapper productMapper;

    public CareRecommendationServiceImpl(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    /**
     * 纯规则推荐：不调用 AiModelClient，结论只由商品属性和宠物档案算出。
     * 因此模型故障不会让推荐环节抛错，AI 只在其上游负责解析意图和生成说明文案。
     */
    @Override
    public List<CareRecommendationResponse> recommend(
            Pet pet, String intent, String itemType,
            List<String> healthTags, int limit) {
        String normalizedType = normalizeType(itemType);
        List<String> requestedTags = normalizeRequestedTags(healthTags);
        List<String> keywords = tokenize(intent);
        // limit 钳制在 1..5：上游 limit 来自 AI 解析结果，可能越界，这里再兜一次，避免一次返回过量条目。
        int safeLimit = Math.min(Math.max(limit, 1), MAX_RESULTS);
        List<ScoredProduct> candidates = new ArrayList<>();
        // 商品查询的三个条件全部传 null：推荐的是平台级在售清单，用户差异只通过 pet 档案（物种、生命阶段）体现，不按用户过滤数据。
        if (TYPE_ALL.equals(normalizedType) || TYPE_GOODS.equals(normalizedType)) {
            collectCandidates(candidates, pet,
                    productMapper.selectForUser(null, null, null, 0, CANDIDATE_LIMIT),
                    requestedTags, keywords);
        }
        if (TYPE_ALL.equals(normalizedType) || TYPE_SERVICE.equals(normalizedType)) {
            collectCandidates(candidates, pet,
                    productMapper.selectServicesForUser(null, null, null, 0, CANDIDATE_LIMIT),
                    requestedTags, keywords);
        }
        return candidates.stream()
                // 同分按价格升序兜底：保证同一批候选每次返回顺序一致，前端展示和用户对照不会跳动。
                .sorted(Comparator.comparingInt(ScoredProduct::score).reversed()
                        .thenComparing(item -> item.product().getPrice()))
                .limit(safeLimit)
                .map(item -> toResponse(item.product(), item.score(), item.matchedTags()))
                .toList();
    }

    private void collectCandidates(List<ScoredProduct> candidates, Pet pet,
                                   List<Product> products, List<String> requestedTags,
                                   List<String> keywords) {
        for (Product product : products) {
            // 三层硬过滤先做：上下架/库存、目标宠物、生命阶段，任一不满足直接跳过，不进入打分，保证结果里不会出现下架或错物种的条目。
            if (!isAvailable(product) || !matchesTargetPet(product, pet)
                    || !matchesLifeStage(product, pet)) {
                continue;
            }
            List<String> productTags = splitTags(product.getHealthTags());
            // 标签用双向 contains 匹配：health_tags 是自由文本，“肠胃敏感”与“肠胃”互相包含都算命中，避免写法差异导致漏匹配。
            List<String> matchedTags = productTags.stream()
                    .filter(tag -> requestedTags.stream().anyMatch(
                            requested -> containsIgnoreCase(tag, requested)
                                    || containsIgnoreCase(requested, tag)))
                    .toList();
            int keywordScore = keywordScore(product, keywords);
            // 只有显式给了健康标签才做“无命中也淘汰”：无标签请求保留全部候选按分排序，否则意图里没有健康词时会返回空列表。
            if (!requestedTags.isEmpty() && matchedTags.isEmpty() && keywordScore == 0) {
                continue;
            }
            // 权重体现优先级：一条标签命中 8 分远高于一个关键词命中 3 分，基础分 10 让零命中的候选仍有稳定排序位置。
            int score = 10 + matchedTags.size() * 8 + keywordScore * 3;
            candidates.add(new ScoredProduct(product, score, matchedTags));
        }
    }

    private boolean isAvailable(Product product) {
        // 商品必须上架且有库存才算可推荐；服务类不占库存，只校验 status='on'，否则会把已售罄的商品推给用户。
        return "on".equals(product.getStatus())
                && (!"goods".equals(product.getProductType())
                || product.getStock() != null && product.getStock() > 0);
    }

    private boolean matchesTargetPet(Product product, Pet pet) {
        String target = normalizeAttribute(product.getTargetPet(), "ALL");
        String species = resolveSpecies(pet.getSpecies());
        return "ALL".equals(target) || "ALL".equals(species) || target.equals(species);
    }

    private boolean matchesLifeStage(Product product, Pet pet) {
        // 宠物没填生日（或生日在未来）时阶段为 null，此时不参与阶段过滤：档案不全不应把候选全部清空。
        String targetStage = normalizeAttribute(product.getLifeStage(), "ALL");
        String petStage = resolveLifeStage(pet.getBirthDate());
        return "ALL".equals(targetStage) || petStage == null || targetStage.equals(petStage);
    }

    private String resolveSpecies(String species) {
        if (species == null) {
            return "ALL";
        }
        String normalized = species.trim();
        if ("猫".equals(normalized) || "cat".equalsIgnoreCase(normalized)) {
            return "CAT";
        }
        if ("狗".equals(normalized) || "犬".equals(normalized)
                || "dog".equalsIgnoreCase(normalized)) {
            return "DOG";
        }
        return "ALL";
    }

    private String resolveLifeStage(LocalDate birthDate) {
        if (birthDate == null || birthDate.isAfter(LocalDate.now())) {
            return null;
        }
        // 月龄按年+月累计而不是天数除以 30：按 30 天折算会让 2 月出生的宠物在 11 个月零几天时被算成 12 个月，幼年/成年边界来回跳。
        int months = Period.between(birthDate, LocalDate.now()).getYears() * 12
                + Period.between(birthDate, LocalDate.now()).getMonths();
        if (months < YOUNG_MAX_MONTHS) {
            return "YOUNG";
        }
        if (months >= SENIOR_MIN_MONTHS) {
            return "SENIOR";
        }
        return "ADULT";
    }

    private int keywordScore(Product product, List<String> keywords) {
        if (keywords.isEmpty()) {
            return 0;
        }
        String content = String.join(" ",
                value(product.getName()), value(product.getDescription()),
                value(product.getCategoryName()), value(product.getHealthTags()))
                .toLowerCase(Locale.ROOT);
        int score = 0;
        for (String keyword : keywords) {
            if (content.contains(keyword.toLowerCase(Locale.ROOT))) {
                score++;
            }
        }
        return score;
    }

    private CareRecommendationResponse toResponse(
            Product product, int score, List<String> matchedTags) {
        boolean goods = "goods".equals(product.getProductType());
        String reason = buildReason(product, matchedTags, score);
        // available 固定为 true：能走到这里的候选都已过 isAvailable，前端据此显示“去购买/去预约”而不是“已下架”。
        return new CareRecommendationResponse(
                product.getId(), product.getProductType(), product.getName(),
                product.getCategoryName(), product.getTargetPet(), product.getLifeStage(),
                splitTags(product.getHealthTags()), product.getPrice(), product.getImageUrl(),
                product.getStock(), true, reason,
                goods ? "去购买" : "去预约",
                (goods ? "/mall/" : "/services/") + product.getId());
    }

    private String buildReason(Product product, List<String> matchedTags, int score) {
        // 理由只拼商品档案里真实存在的 targetPet、lifeStage 和命中标签，不写功效文案，避免推荐理由被当成医疗承诺。
        List<String> parts = new ArrayList<>();
        parts.add("适合" + displayTargetPet(product.getTargetPet()));
        if (!"ALL".equals(normalizeAttribute(product.getLifeStage(), "ALL"))) {
            parts.add(displayLifeStage(product.getLifeStage()));
        }
        if (!matchedTags.isEmpty()) {
            parts.add("匹配标签：" + String.join("、", matchedTags));
        } else if (score > 10) {
            parts.add("与查询内容相关");
        }
        return String.join(" · ", parts);
    }

    private String displayTargetPet(String value) {
        return switch (normalizeAttribute(value, "ALL")) {
            case "CAT" -> "猫咪";
            case "DOG" -> "狗狗";
            default -> "猫狗";
        };
    }

    private String displayLifeStage(String value) {
        return switch (normalizeAttribute(value, "ALL")) {
            case "YOUNG" -> "幼年阶段";
            case "ADULT" -> "成年阶段";
            case "SENIOR" -> "老年阶段";
            default -> "全阶段";
        };
    }

    private List<String> normalizeRequestedTags(List<String> healthTags) {
        if (healthTags == null || healthTags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String item : healthTags) {
            if (item != null && !item.isBlank()) {
                result.add(item.trim());
            }
        }
        return List.copyOf(result);
    }

    private List<String> splitTags(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[\\s,，;；、]+"))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
    }

    private List<String> tokenize(String intent) {
        if (intent == null || intent.isBlank()) {
            return List.of();
        }
        return Arrays.stream(intent.split("[\\s,，。；;、]+"))
                .map(String::trim)
                // 只保留长度≥2 且非停用词的片段：中文单字关键词命中率过高，会把无关商品一起加分。
                .filter(item -> item.length() >= 2)
                .filter(item -> !STOP_WORDS.contains(item))
                .distinct()
                .toList();
    }

    private String normalizeType(String itemType) {
        if (itemType == null || itemType.isBlank()) {
            return TYPE_ALL;
        }
        String value = itemType.trim().toUpperCase(Locale.ROOT);
        // 非法 itemType 退回 ALL 而不是报错：取值来自 AI 解析结果，脏值时仍要给出可用推荐，不能整条请求失败。
        return switch (value) {
            case TYPE_GOODS, TYPE_SERVICE, TYPE_ALL -> value;
            default -> TYPE_ALL;
        };
    }

    private String normalizeAttribute(String value, String defaultValue) {
        return value == null || value.isBlank()
                ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean containsIgnoreCase(String source, String target) {
        return source != null && target != null
                && source.toLowerCase(Locale.ROOT).contains(target.toLowerCase(Locale.ROOT));
    }

    private String value(String text) {
        return text == null ? "" : text;
    }

    private record ScoredProduct(Product product, int score, List<String> matchedTags) {
    }
}
