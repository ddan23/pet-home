package com.hnist.service;

import com.hnist.domain.AiAdvisorResult;
import com.hnist.domain.HealthChatMessage;
import com.hnist.entity.Pet;

import java.util.List;
import java.util.function.Consumer;

/** 宠物商品与养护服务推荐。 */
public interface HealthRecommendationService {

    /** 模型未配置或调用失败抛 AiUnavailableException（上层转 503）；超出范围或无候选时返回带 category 标记的结果，不抛业务异常。 */
    AiAdvisorResult recommend(Integer userId, Pet pet, String message,
                              List<HealthChatMessage> history);

    /** 与 recommend 同一套校验和降级逻辑，区别只是生成阶段通过 onDelta 增量推送，最终仍返回完整答案。 */
    AiAdvisorResult recommendStream(Integer userId, Pet pet, String message,
                                    List<HealthChatMessage> history, Consumer<String> onDelta);
}
