package com.hnist.domain;

import lombok.Data;

import java.util.List;

/** AI 推荐意图：模型解析出的检索条件，字段默认值保证解析失败时仍可发起一次全量推荐。 */
@Data
public class AiRecommendationIntent {

    /** 只认 GOODS、SERVICE，其余脏值在解析时退回 ALL。 */
    private String itemType = "ALL";

    /** 去掉停用词后的检索关键词来源，原样作为意图文本。 */
    private String intent;

    /** 空列表表示不做标签淘汰，只按关键词与档案打分。 */
    private List<String> healthTags = List.of();

    /** 期望返回条数，解析后已钳制在 1~5。 */
    private Integer limit = 5;
}
