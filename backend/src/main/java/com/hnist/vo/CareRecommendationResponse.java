package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** 养宠顾问推荐结果：候选已在服务端按上下架、物种、生命阶段过滤，前端直接渲染即可下单。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CareRecommendationResponse {

    /** 商品/服务 id，与 itemType 一起构成跳转定位。 */
    private Integer itemId;

    /** 取值 goods 或 service，决定 actionLabel 是“去购买”还是“去预约”。 */
    private String itemType;

    private String name;

    private String categoryName;

    private String targetPet;

    private String lifeStage;

    private List<String> healthTags;

    /** 单价（元），取自商品表当前价格。 */
    private BigDecimal price;

    private String imageUrl;

    /** 服务类不占库存，此处可能为 null。 */
    private Integer stock;

    /** 能进结果集的条目都已通过可用性过滤，因此恒为 true，前端据此显示可下单入口。 */
    private Boolean available;

    /** 推荐理由：只拼档案里的目标宠物、生命阶段和命中标签，不写功效表述。 */
    private String reason;

    private String actionLabel;

    /** 前端跳转路径，如 /mall/{id} 或 /services/{id}。 */
    private String actionPath;
}
