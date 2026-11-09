package com.hnist.service;

import com.hnist.domain.HealthCitation;
import com.hnist.entity.KnowledgeChunk;

import java.util.List;

/** 健康问答的知识检索入口：取回已发布文章分块并转成可追溯引用。 */
public interface KnowledgeRetrievalService {

    /** 检索失败按实现降级（Milvus → MySQL 全文），查询为空时返回空集合而不是抛异常。 */
    List<KnowledgeChunk> retrieve(String query, String species);

    /** 同一文章多个分块只产出一条引用，referenceId 形如 S1、S2，供模型在回答正文中标注来源。 */
    List<HealthCitation> toCitations(List<KnowledgeChunk> chunks);
}
