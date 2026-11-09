package com.hnist.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** knowledge_articles 表行映射：经过审核的宠物健康知识文章。 */
@Data
public class KnowledgeArticle {

    private Long id;

    private String title;

    private String species;

    private String topic;

    private String summary;

    private String sourceTitle;

    private String sourceUrl;

    private String publisher;

    private LocalDate publishedOn;

    /** 仅 published 时写入，草稿与归档为 null */
    private LocalDate reviewedOn;

    /** 审核状态：draft、published、archived（KnowledgeStatus） */
    private String reviewStatus;

    /** 仅 published 时写入 */
    private Integer reviewerId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
