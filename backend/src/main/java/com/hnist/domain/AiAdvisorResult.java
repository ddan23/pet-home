package com.hnist.domain;

import com.hnist.vo.CareRecommendationResponse;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** AI 养宠顾问内部生成结果：先在本层聚合，再由流式服务落库为 ai_chat_messages 并转给前端。 */
@Data
@AllArgsConstructor
public class AiAdvisorResult {

    private String answer;

    /** true 表示未真正作答（越界、检索空、引用缺失、舒适区外），与 emergency 互相独立。 */
    private boolean refused;

    /** 紧急信号，前端需优先展示就诊提示。 */
    private boolean emergency;

    /** 分类标记，拒答时区分 OUT_OF_SCOPE、INSUFFICIENT_EVIDENCE、CITATION_REQUIRED、NO_MATCH 等成因。 */
    private String category;

    private String disclaimer;

    /** 取值 TEXT（问答）或 RECOMMENDATION（推荐），决定前端按纯文本还是推荐卡片渲染。 */
    private String messageType;

    private List<HealthCitation> citations;

    /** 推荐模式下才非空，且已按宠物过敏原过滤，不会推出过敏条目。 */
    private List<CareRecommendationResponse> recommendations;
}
