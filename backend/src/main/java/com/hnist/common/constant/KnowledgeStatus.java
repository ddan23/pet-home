package com.hnist.common.constant;

/** 知识文章审核状态，落 knowledge_articles.review_status；只有 published 会进入检索索引。 */
public enum KnowledgeStatus {

    /** 草稿：库表默认值，不参与检索，审核人与审核日期保持为空。 */
    DRAFT("draft"),
    /** 已发布：人工发布后写入并记录审核人/日期，异步向量化后进入 RAG 检索。 */
    PUBLISHED("published"),
    /** 已归档：下线后写入，异步任务会删除该文章在 Milvus 中的向量。 */
    ARCHIVED("archived");

    private final String code;

    KnowledgeStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
