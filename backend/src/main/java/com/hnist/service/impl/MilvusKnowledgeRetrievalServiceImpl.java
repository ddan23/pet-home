package com.hnist.service.impl;

import com.hnist.config.RagProperties;
import com.hnist.domain.HealthCitation;
import com.hnist.entity.KnowledgeChunk;
import com.hnist.service.KnowledgeRetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识检索主实现：Spring AI 官方 Milvus VectorStore 稠密向量检索 + MySQL 全文检索两路召回，
 * 在应用层做 RRF（倒数排名融合）。原 Milvus 侧 BM25 稀疏通道随官方化改造移除，
 * 其召回职责由 MySQL FULLTEXT 通道承接，融合算法与 topK 口径保持不变。
 * 任何向量侧失败（未启用、连不上、超时）都退回 MySQL 全文检索。
 */
@Slf4j
@Primary
@Service
public class MilvusKnowledgeRetrievalServiceImpl implements KnowledgeRetrievalService {

    private static final int MAX_QUERY_LENGTH = 200;

    private static final String SPECIES_ALL = "all";

    private final RagProperties ragProperties;

    private final ObjectProvider<VectorStore> vectorStoreProvider;

    private final KnowledgeRetrievalServiceImpl keywordRetriever;

    public MilvusKnowledgeRetrievalServiceImpl(RagProperties ragProperties,
                                               ObjectProvider<VectorStore> vectorStoreProvider,
                                               KnowledgeRetrievalServiceImpl keywordRetriever) {
        this.ragProperties = ragProperties;
        this.vectorStoreProvider = vectorStoreProvider;
        this.keywordRetriever = keywordRetriever;
    }

    @Override
    public List<KnowledgeChunk> retrieve(String query, String species) {
        // RAG 未配置（enabled=false 或缺少 embeddingApiKey）时不尝试连接：直接走关键词路径，
        // 否则每次问答都要等一次连接失败。
        if (!ragProperties.isReady() || vectorStoreProvider.getIfAvailable() == null) {
            return keywordRetriever.retrieve(query, species);
        }
        String safeQuery = normalizeQuery(query);
        if (safeQuery.isBlank()) {
            return List.of();
        }
        try {
            // 两路各召回 topK×candidateMultiplier 条候选再融合截断：候选数与最终条数 1:1 时
            // RRF 只能在两路结果的交集里排序，稠密与关键词的互补召回就失去意义了。
            int candidateLimit = Math.max(
                    ragProperties.getTopK() * ragProperties.getCandidateMultiplier(),
                    ragProperties.getTopK());
            List<KnowledgeChunk> vectorHits = vectorSearch(safeQuery, species, candidateLimit);
            List<KnowledgeChunk> keywordHits = keywordRetriever.retrieve(query, species);
            return fuse(vectorHits, keywordHits, ragProperties.getTopK());
        } catch (RuntimeException exception) {
            // embedding 超时、Milvus 连不上、集合未 load 都会到这里：知识检索只是健康问答的证据增强，
            // 不能因为这层不可用就让整轮问答失败，降级到 MySQL 全文仍能给出可追溯来源。
            log.warn("向量检索失败，降级为 MySQL 全文检索，query={}", safeQuery, exception);
            return keywordRetriever.retrieve(query, species);
        }
    }

    @Override
    public List<HealthCitation> toCitations(List<KnowledgeChunk> chunks) {
        return keywordRetriever.toCitations(chunks);
    }

    /**
     * 向量单路召回：similaritySearch 内部用装配的 EmbeddingModel 把查询向量化。
     * species 在应用层过滤而不是下推到向量库过滤表达式：元数据在 Milvus 里是 JSON 字段，
     * 应用层过滤同样准确，且不依赖 Spring AI 元数据字段的 schema 声明；被过滤的候选由扩大后的
     * candidateLimit 兜底，通常仍有足够的 topK 条返回。
     */
    private List<KnowledgeChunk> vectorSearch(String query, String species, int candidateLimit) {
        String resolvedSpecies = KnowledgeRetrievalServiceImpl.resolveSpeciesByKeywords(species, query);
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(candidateLimit)
                .build();
        List<Document> documents = vectorStoreProvider.getObject().similaritySearch(request);
        List<KnowledgeChunk> chunks = new ArrayList<>();
        if (documents == null) {
            return chunks;
        }
        for (Document document : documents) {
            Map<String, Object> meta = document.getMetadata();
            String docSpecies = String.valueOf(meta.getOrDefault("species", SPECIES_ALL));
            // 物种口径与写入时对齐（缺省 all）：既过滤掉另一物种的剂量类知识，也保留跨物种通用知识。
            if (!SPECIES_ALL.equals(docSpecies) && !docSpecies.equals(resolvedSpecies)) {
                continue;
            }
            chunks.add(toChunk(document, meta));
        }
        return chunks;
    }

    private KnowledgeChunk toChunk(Document document, Map<String, Object> meta) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setArticleId(asLong(meta.get("articleId")));
        chunk.setContent(document.getText() == null ? "" : document.getText());
        chunk.setTitle(asString(meta.get("title")));
        chunk.setSourceTitle(asString(meta.get("sourceTitle")));
        chunk.setSourceUrl(asString(meta.get("sourceUrl")));
        chunk.setPublisher(asString(meta.get("publisher")));
        Object reviewedOn = meta.get("reviewedOn");
        chunk.setReviewedOn(reviewedOn == null || String.valueOf(reviewedOn).isBlank()
                ? null : java.time.LocalDate.parse(String.valueOf(reviewedOn)));
        // 相似度得分只用于排序展示，不能与固定阈值比较：换 embedding 模型后分数量纲会整体漂移。
        chunk.setRelevance(document.getScore());
        return chunk;
    }

    /**
     * RRF 倒数排名融合：score = Σ 1/(k + rank)，只依赖各路排名不依赖分数量纲，
     * 余弦相似度与 FULLTEXT 相关度可以安全混合；k 来自 app.ai.rag.rrf-k 配置便于调优。
     * 同一分块可能两路同时命中，按 articleId + 内容判重后名次累加，天然置顶强相关结果。
     */
    private List<KnowledgeChunk> fuse(List<KnowledgeChunk> vectorHits,
                                      List<KnowledgeChunk> keywordHits, int topK) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, KnowledgeChunk> unique = new HashMap<>();
        accumulate(vectorHits, scores, unique, ragProperties.getRrfK());
        accumulate(keywordHits, scores, unique, ragProperties.getRrfK());
        List<KnowledgeChunk> fused = new ArrayList<>(unique.values());
        fused.sort((a, b) -> Double.compare(
                scores.get(fuseKey(b)), scores.get(fuseKey(a))));
        return fused.size() > topK ? new ArrayList<>(fused.subList(0, topK)) : fused;
    }

    private void accumulate(List<KnowledgeChunk> hits, Map<String, Double> scores,
                            Map<String, KnowledgeChunk> unique, int rrfK) {
        for (int rank = 0; rank < hits.size(); rank++) {
            KnowledgeChunk chunk = hits.get(rank);
            String key = fuseKey(chunk);
            unique.putIfAbsent(key, chunk);
            scores.merge(key, 1.0D / (rrfK + rank + 1), Double::sum);
        }
    }

    private String fuseKey(KnowledgeChunk chunk) {
        return chunk.getArticleId() + ":" + chunk.getContent();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    // 向量链路只截断：截到 MAX_QUERY_LENGTH 是为了控制 embedding 请求体积和耗时，
    // 超长问句的额外字符不会提升召回。
    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        String value = query.trim();
        return value.length() > MAX_QUERY_LENGTH ? value.substring(0, MAX_QUERY_LENGTH) : value;
    }
}
