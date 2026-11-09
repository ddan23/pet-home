package com.hnist.service;

import com.hnist.vo.KnowledgeArticleDetailResponse;
import com.hnist.dto.SaveKnowledgeArticleRequest;
import com.hnist.entity.KnowledgeArticle;
import com.hnist.common.result.PageResult;

/** 健康知识文章的管理端维护：分块落库、审核状态流转与索引同步。 */
public interface KnowledgeBaseService {

    PageResult<KnowledgeArticle> list(String keyword, String status, int page, int pageSize);

    KnowledgeArticleDetailResponse detail(Long id);

    /** 保存会整体重建该文章的分块并异步同步向量索引；正文为空时抛业务异常，不落库。 */
    KnowledgeArticle save(Long id, SaveKnowledgeArticleRequest request, Integer reviewerId);

    /** 发布前置条件是已有正文分块，否则报错；发布后 reviewed_on 由 updateReviewStatus 刷新为当天。 */
    void publish(Long id, Integer reviewerId);

    /** 归档即下线：状态变更后该文章的向量会被删除，检索不再返回。 */
    void archive(Long id, Integer reviewerId);

    /** 删除文章（分块随外键级联删除）并清理 Milvus 中该 articleId 的向量。 */
    void delete(Long id);
}
