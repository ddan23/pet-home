package com.hnist.service;

import com.hnist.domain.HealthChatMessage;
import com.hnist.domain.PetHealthContext;
import com.hnist.dto.AiHealthChatRequest;
import com.hnist.vo.AiHealthChatResponse;

import java.util.List;
import java.util.function.Consumer;

/** 猫狗健康助手服务。 */
public interface HealthAssistantService {

    AiHealthChatResponse chat(Integer userId, AiHealthChatRequest request);

    /**
     * 契约：知识库检索不到证据时不调用生成模型，直接返回拒答结果；正文未带 [S数字] 引用时同样拒答。
     * 模型未配置或调用失败抛 AiUnavailableException（上层转 503），不返回降级文案。
     */
    AiHealthChatResponse chat(Integer userId, PetHealthContext pet,
                              String message, List<HealthChatMessage> history);

    /** 与 chat 相同的证据与校验流程，区别只是生成阶段通过 onDelta 增量推送。 */
    AiHealthChatResponse chatStream(Integer userId, PetHealthContext pet, String message,
                                    List<HealthChatMessage> history, Consumer<String> onDelta);
}
