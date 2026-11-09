package com.hnist.entity;

import lombok.Data;

import java.time.LocalDate;

/** knowledge_chunks 表行映射：用于 RAG 检索的知识分块及其来源信息。 */
@Data
public class KnowledgeChunk {

    private Long id;

    private Long articleId;

    /** title 到 reviewedOn 来自关联的 knowledge_articles，不是分块表自身的列 */
    private String title;

    private String species;

    private String topic;

    private String content;

    /** 检索关键词，空格分隔 */
    private String searchKeywords;

    /** 分块在文章内的顺序，从 0 开始 */
    private Integer chunkOrder;

    private String sourceTitle;

    private String sourceUrl;

    private String publisher;

    private LocalDate reviewedOn;

    /** 非DB字段，向量检索命中的相关度得分 */
    private Double relevance;
}
