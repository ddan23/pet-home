package com.hnist.service.impl;

import com.hnist.service.KnowledgeRetrievalService;

import com.hnist.domain.HealthCitation;
import com.hnist.mapper.KnowledgeMapper;
import com.hnist.entity.KnowledgeChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MySQL 全文检索实现，也是向量检索不可用时的降级路径。 */
@Service
public class KnowledgeRetrievalServiceImpl implements KnowledgeRetrievalService {

    // 查询截到 200 字符：超长问句在 NATURAL LANGUAGE MODE 下几乎不增加召回，只会拉长 FULLTEXT 扫描时间。
    private static final int MAX_QUERY_LENGTH = 200;

    // 降级路径固定取 4 条，与 app.ai.rag.top-k 默认值一致，保证降级后送入模型的证据条数不突变。
    private static final int DEFAULT_LIMIT = 4;

    private final KnowledgeMapper knowledgeMapper;

    public KnowledgeRetrievalServiceImpl(KnowledgeMapper knowledgeMapper) {
        this.knowledgeMapper = knowledgeMapper;
    }

    @Override
    public List<KnowledgeChunk> retrieve(String query, String species) {
        String safeQuery = normalizeQuery(query);
        // 空查询返回空集合而不是全量兜底：健康问答没有具体问题时，随便塞几段知识会误导模型给出无关结论。
        if (safeQuery.isBlank()) {
            return List.of();
        }
        // 物种口径必须与写入 Milvus 时一致（cat/dog/all），否则 SQL 里 ka.species 过滤不到，检索直接空结果。
        String safeSpecies = resolveSpeciesByKeywords(species, safeQuery);
        // 可见性过滤在 searchPublished 的 SQL 内完成：只有 review_status='published' 且 reviewed_on 非空、
        // 并且 MATCH 相关度 > 0 的文章分块才会返回，草稿与归档内容永远进不了证据链。
        List<KnowledgeChunk> chunks = knowledgeMapper.searchPublished(
                safeQuery, safeSpecies, DEFAULT_LIMIT);
        return chunks == null ? List.of() : chunks;
    }

    @Override
    public List<HealthCitation> toCitations(List<KnowledgeChunk> chunks) {
        Map<Long, HealthCitation> citations = new LinkedHashMap<>();
        int index = 1;
        // 同一文章可能命中多个分块，按 articleId 去重并保留首次出现顺序：引用编号 S1、S2 要和
        // buildEvidence 的输出顺序对齐，重复来源不能占两个编号。
        for (KnowledgeChunk chunk : chunks) {
            if (!citations.containsKey(chunk.getArticleId())) {
                citations.put(chunk.getArticleId(), new HealthCitation(
                        "S" + index++,
                        chunk.getSourceTitle(),
                        chunk.getPublisher(),
                        chunk.getSourceUrl(),
                        chunk.getReviewedOn() == null ? null : chunk.getReviewedOn().toString()
                ));
            }
        }
        return new ArrayList<>(citations.values());
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        // 去掉 + - < > ( ) ~ * " @ 等全文检索语法符号：用户正文里带这些字符时会被当成检索表达式，
        // 轻则改写语义，重则让 MATCH ... AGAINST 直接报语法错误把问答打断。
        String value = query.replaceAll("[+\\-<>()~*\"@]", " ").trim();
        return value.length() > MAX_QUERY_LENGTH
                ? value.substring(0, MAX_QUERY_LENGTH) : value;
    }

    // 未传物种时按查询词反推：过滤条件是 species in ('all', 具体物种)，猜错会把另一半知识库整体过滤掉；
    // 只有明确出现「狗/犬」且不含「猫」才判 dog，其余默认 cat，避免算出一个不存在的物种值导致空结果。
    static String resolveSpeciesByKeywords(String species, String query) {
        if ("猫".equals(species) || "cat".equalsIgnoreCase(species)) {
            return "cat";
        }
        if ("狗".equals(species) || "犬".equals(species)
                || "dog".equalsIgnoreCase(species)) {
            return "dog";
        }
        return query.matches(".*(狗|犬).*") && !query.matches(".*猫.*") ? "dog" : "cat";
    }
}
