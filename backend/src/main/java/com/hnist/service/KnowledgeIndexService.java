package com.hnist.service;

/** 知识库向量索引服务，负责把 MySQL 中已发布的知识文章同步到 Milvus。 */
public interface KnowledgeIndexService {

    /** 返回 RAG 是否已配置可用；未就绪时各同步方法静默空转，调用方不能把「没报错」当成索引成功。 */
    boolean isReady();

    /**
     * 幂等：先按 articleId 删除旧向量再写入，重复同步不会留下重复分块。
     * 未发布、已删除或没有分块的文章转为删除索引；向量化失败抛 AiUnavailableException，旧向量保持原样。
     */
    void syncArticle(Long articleId);

    /** 按 articleId 清空该文章全部向量，是文章被删除后唯一的向量清理入口；未启用 RAG 时静默返回。 */
    void removeArticle(Long articleId);

    /** 只重建 published 文章且最多 500 篇，单篇失败只跳过不中断整批；返回实际同步成功篇数，未启用 RAG 返回 0。 */
    int rebuildAll();
}
