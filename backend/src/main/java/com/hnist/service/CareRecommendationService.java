package com.hnist.service;

import com.hnist.vo.CareRecommendationResponse;
import com.hnist.entity.Pet;

import java.util.List;

/** 基于宠物档案和商品养护属性的规则推荐，返回平台当前在售的条目。 */
public interface CareRecommendationService {

    /**
     * 纯规则匹配、不调用模型，AI 不可用不影响本方法；只返回上架、有库存且与 pet 物种和生命阶段匹配的条目。
     * limit 会被钳制在 1..5，无候选时返回空列表而不是抛异常。
     */
    List<CareRecommendationResponse> recommend(
            Pet pet, String intent, String itemType,
            List<String> healthTags, int limit);
}
