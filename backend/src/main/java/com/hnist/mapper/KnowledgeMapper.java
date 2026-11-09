package com.hnist.mapper;

import com.hnist.entity.KnowledgeArticle;
import com.hnist.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 健康知识库数据访问。 */
public interface KnowledgeMapper {

    /** 全文检索 MATCH ... AGAINST，仅返回 review_status = 'published' 且 reviewed_on 非空、物种匹配（或 'all'）的片段。 */
    List<KnowledgeChunk> searchPublished(@Param("query") String query,
                                         @Param("species") String species,
                                         @Param("limit") int limit);

    List<KnowledgeArticle> selectAll(@Param("keyword") String keyword,
                                     @Param("status") String status,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    int countAll(@Param("keyword") String keyword, @Param("status") String status);

    KnowledgeArticle selectById(@Param("id") Long id);

    List<KnowledgeChunk> selectChunksByArticleId(@Param("articleId") Long articleId);

    int insertArticle(KnowledgeArticle article);

    int updateArticle(KnowledgeArticle article);

    /** 仅当 reviewStatus = 'published' 时才把 reviewed_on 刷成当天，其余状态沿用原值。 */
    int updateReviewStatus(@Param("id") Long id,
                           @Param("reviewStatus") String reviewStatus,
                           @Param("reviewerId") Integer reviewerId);

    int deleteChunks(@Param("articleId") Long articleId);

    int insertChunk(KnowledgeChunk chunk);

    int deleteArticle(@Param("id") Long id);
}
