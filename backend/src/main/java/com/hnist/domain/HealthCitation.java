package com.hnist.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 健康回答引用的可追溯来源：编号与正文里的 [S1] 标记一一对应。 */
@Data
@AllArgsConstructor
public class HealthCitation {

    /** 引用编号，按命中顺序从 S1 递增；同一文章多个分块只占一个编号。 */
    private String referenceId;

    /** 文章标题，取自知识库分块元数据。 */
    private String title;

    private String publisher;

    private String sourceUrl;

    /** 复核日期，知识库中存的是日期，这里以字符串透出，不做格式转换。 */
    private String reviewedOn;
}
