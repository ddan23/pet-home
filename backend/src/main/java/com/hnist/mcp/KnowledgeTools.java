package com.hnist.mcp;

import com.hnist.domain.HealthCitation;
import com.hnist.entity.KnowledgeChunk;
import com.hnist.service.KnowledgeRetrievalService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 宠物健康知识库 MCP 工具：把 RAG 检索能力以只读工具形式暴露给外部 AI 应用。 */
public class KnowledgeTools {

    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final ObjectMapper objectMapper;

    public KnowledgeTools(KnowledgeRetrievalService knowledgeRetrievalService, ObjectMapper objectMapper) {
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.objectMapper = objectMapper;
    }

    /** 语义检索已发布的健康科普知识，返回证据分块与可追溯引用。 */
    @Tool(name = "search_pet_knowledge",
            description = "在平台已发布的猫狗健康科普知识库中做语义检索（向量检索，失败自动降级 MySQL 全文）。"
                    + "返回相关文章分块正文与引用列表（referenceId 形如 S1、S2），回答正文应在对应结论处标注引用编号。"
                    + "查询为空或无命中时返回空数组，不视为错误。")
    public String searchPetKnowledge(
            @ToolParam(description = "自然语言查询，例如：幼犬疫苗间隔多久打一次") String query,
            @ToolParam(description = "宠物物种过滤：cat / dog / all；不确定宠物物种时传 all",
                    required = false) String species) {
        // 检索一次，分块与引用共用同一批结果；实现侧已保证空查询返回空集合、草稿内容不可见
        List<KnowledgeChunk> chunks = knowledgeRetrievalService.retrieve(query, species);
        List<HealthCitation> citations = knowledgeRetrievalService.toCitations(chunks);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chunks", chunks.stream().map(this::chunkView).toList());
        data.put("citations", citations);
        return toJson(data);
    }

    // 分块只透出检索证据所需字段，searchKeywords 等内部检索元数据不外发
    private Map<String, Object> chunkView(KnowledgeChunk chunk) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("articleId", chunk.getArticleId());
        view.put("title", chunk.getTitle());
        view.put("topic", chunk.getTopic());
        view.put("species", chunk.getSpecies());
        view.put("content", chunk.getContent());
        view.put("relevance", chunk.getRelevance());
        return view;
    }

    private String toJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            // 不吞异常：向上抛让 MCP 协议层返回 tool 执行错误
            throw new IllegalStateException("知识检索结果序列化失败", e);
        }
    }
}
