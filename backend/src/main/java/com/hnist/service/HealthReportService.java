package com.hnist.service;

import com.hnist.domain.AiAdvisorResult;
import com.hnist.domain.HealthChatMessage;
import com.hnist.entity.Pet;

import java.util.List;

/** 依据宠物档案与已审核知识证据生成 HEALTH_REPORT 正文和引用。 */
public interface HealthReportService {

    /**
     * 按 userId 计一次 AI 用量，超限抛 429 业务异常；未配置模型或调用失败抛 AiUnavailableException，由全局处理成 503。
     * 返回的 messageType 固定为 HEALTH_REPORT 供 PDF 导出识别，正文越界时整段替换为档案兜底版本。
     */
    AiAdvisorResult generate(Integer userId, Pet pet, List<HealthChatMessage> history);
}
