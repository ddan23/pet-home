package com.hnist.vo;

import com.hnist.entity.KnowledgeArticle;
import com.hnist.entity.KnowledgeChunk;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 知识库文章详情：正文元数据与检索分块一起返回，供后台核对引用来源。 */
@Data
@AllArgsConstructor
public class KnowledgeArticleDetailResponse {

    private KnowledgeArticle article;

    /** 该文章切分后的检索单元，用于核对健康回答引用的命中位置。 */
    private List<KnowledgeChunk> chunks;
}
