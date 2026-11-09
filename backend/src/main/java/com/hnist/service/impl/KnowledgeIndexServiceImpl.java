package com.hnist.service.impl;

import com.hnist.common.constant.KnowledgeStatus;
import com.hnist.config.RagProperties;
import com.hnist.entity.KnowledgeArticle;
import com.hnist.entity.KnowledgeChunk;
import com.hnist.mapper.KnowledgeMapper;
import com.hnist.service.KnowledgeIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把 MySQL 中已发布知识文章的分块写入 Spring AI 官方 Milvus VectorStore，并负责全量重建。
 * 向量化由 VectorStore.add 内部调用装配的 EmbeddingModel 完成；集合结构由 Spring AI 官方
 * initialize-schema 维护（doc_id/content/metadata/embedding），应用不再自建 Milvus 集合。
 */
@Slf4j
@Service
public class KnowledgeIndexServiceImpl implements KnowledgeIndexService {

    // content 截断与检索口径同源：超长分块会推高 embedding 请求体积，截断值调整后必须 rebuildAll 重建两侧向量。
    private static final int MAX_CONTENT_LENGTH = 2000;

    // 启动重建只取 selectAll 按 updated_at 倒序的前 500 篇：全量重新向量化会拖慢启动并打满 embedding 配额。
    private static final int REBUILD_LIMIT = 500;

    // 兜底删除时按 articleId 过滤检索的候选上限：单篇文章分块数远小于该值，取大只为不漏删。
    private static final int ORPHAN_LOOKUP_LIMIT = 100;

    private static final String SPECIES_ALL = "all";

    private final ObjectProvider<VectorStore> vectorStoreProvider;

    private final RagProperties ragProperties;

    private final KnowledgeMapper knowledgeMapper;

    /**
     * VectorStore 由 Spring AI 自动配置提供，且仅在 RAG 开关打开时装配（见 RagVectorStoreConfig）：
     * 这里用 ObjectProvider 注入，未启用时 isReady 已经挡住所有调用，bean 缺失不会影响启动。
     */
    public KnowledgeIndexServiceImpl(ObjectProvider<VectorStore> vectorStoreProvider,
                                     RagProperties ragProperties,
                                     KnowledgeMapper knowledgeMapper) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.ragProperties = ragProperties;
        this.knowledgeMapper = knowledgeMapper;
    }

    @Override
    public boolean isReady() {
        return ragProperties.isReady() && vectorStoreProvider.getIfAvailable() != null;
    }

    @Override
    public void syncArticle(Long articleId) {
        if (!isReady() || articleId == null) {
            return;
        }
        KnowledgeArticle article = knowledgeMapper.selectById(articleId);
        // 已下线或已删除的文章要反向清理向量：向量库侧没有状态字段，文章表状态是唯一的可见性依据，
        // 不删的话草稿/归档内容仍会被检索命中并当作证据返回给用户。
        if (article == null || !KnowledgeStatus.PUBLISHED.getCode().equals(article.getReviewStatus())) {
            removeArticle(articleId);
            return;
        }
        List<KnowledgeChunk> chunks = knowledgeMapper.selectChunksByArticleId(articleId);
        if (chunks == null || chunks.isEmpty()) {
            removeArticle(articleId);
            return;
        }
        // 幂等：先删旧向量再写入。文章改稿后分块数量可能变少，只追加会残留旧分块；
        // Document id 由 articleId + chunkOrder 稳定复现，重复同步天然覆盖同一行。
        removeArticle(articleId);
        List<Document> documents = new ArrayList<>(chunks.size());
        for (KnowledgeChunk chunk : chunks) {
            documents.add(toDocument(article, chunk));
        }
        // add 内部调用装配的 EmbeddingModel 完成向量化，失败抛 Spring AI 运行时异常由上层降级
        vectorStoreProvider.getObject().add(documents);
        log.info("知识文章索引完成，articleId={}，分块数={}", articleId, documents.size());
    }

    @Override
    public void removeArticle(Long articleId) {
        if (!isReady() || articleId == null) {
            return;
        }
        List<KnowledgeChunk> chunks = knowledgeMapper.selectChunksByArticleId(articleId);
        if (chunks != null && !chunks.isEmpty()) {
            List<String> ids = new ArrayList<>(chunks.size());
            for (KnowledgeChunk chunk : chunks) {
                ids.add(docId(articleId, chunk.getChunkOrder()));
            }
            vectorStoreProvider.getObject().delete(ids);
            return;
        }
        // 文章被物理删除后 MySQL 分块已不存在，只能按 articleId 从向量库反查残留 id 再删；
        // 查询需要一个可向量化的查询串，用空格占位即可，过滤条件承担全部定位职责。
        Document orphan = lookupOrphan(articleId);
        if (orphan != null) {
            vectorStoreProvider.getObject().delete(List.of(orphan.getId()));
        }
    }

    @Override
    public int rebuildAll() {
        if (!isReady()) {
            return 0;
        }
        // 只重建 published 状态：draft/archived 不应出现在检索候选里，同步时会被 syncArticle 判为下线并删除。
        List<KnowledgeArticle> articles = knowledgeMapper.selectAll(null,
                KnowledgeStatus.PUBLISHED.getCode(), 0, REBUILD_LIMIT);
        int synced = 0;
        for (KnowledgeArticle article : articles) {
            // 单篇失败只跳过并记日志：一篇文章的向量化异常不能让整批重建中断，其余文章仍应可用。
            try {
                syncArticle(article.getId());
                synced++;
            } catch (RuntimeException exception) {
                log.warn("知识文章索引失败，articleId={}", article.getId(), exception);
            }
        }
        log.info("知识库索引重建完成，文章数={}", synced);
        return synced;
    }

    private Document toDocument(KnowledgeArticle article, KnowledgeChunk chunk) {
        // species 缺省写 all 而不是空串：检索过滤条件是 species in ["all", 具体物种]，空值会让文章永远命不中。
        return new Document(
                docId(article.getId(), chunk.getChunkOrder()),
                truncate(chunk.getContent(), MAX_CONTENT_LENGTH),
                Map.of(
                        "articleId", article.getId(),
                        "chunkOrder", chunk.getChunkOrder(),
                        "species", defaultIfBlank(article.getSpecies(), SPECIES_ALL),
                        "topic", defaultIfBlank(article.getTopic(), ""),
                        "title", truncate(defaultIfBlank(article.getTitle(), ""), 200),
                        "sourceTitle", truncate(defaultIfBlank(article.getSourceTitle(), ""), 255),
                        "sourceUrl", truncate(defaultIfBlank(article.getSourceUrl(), ""), 500),
                        "publisher", truncate(defaultIfBlank(article.getPublisher(), ""), 120),
                        "reviewedOn", article.getReviewedOn() == null
                                ? "" : article.getReviewedOn().toString()
                ));
    }

    private String docId(Long articleId, Integer chunkOrder) {
        return String.format("a%d_c%d", articleId, chunkOrder);
    }

    /** 文章物理删除后的向量兜底清理：按 articleId 过滤检索一条命中即可拿到残留 Document id。 */
    private Document lookupOrphan(Long articleId) {
        try {
            var request = SearchRequest.builder()
                    .query(" ")
                    .topK(ORPHAN_LOOKUP_LIMIT)
                    .filterExpression(new FilterExpressionTextParser().parse(
                            "articleId == " + articleId))
                    .build();
            List<Document> found = vectorStoreProvider.getObject().similaritySearch(request);
            return found == null || found.isEmpty() ? null : found.get(0);
        } catch (RuntimeException exception) {
            log.warn("清理孤儿向量失败，articleId={}（下次重建可补齐）", articleId, exception);
            return null;
        }
    }

    // 写入前统一截断：与检索内容口径一致，避免超长分块推高 embedding 请求体积与耗时。
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String defaultIfBlank(String text, String defaultValue) {
        return text == null || text.isBlank() ? defaultValue : text;
    }
}
