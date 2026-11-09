package com.hnist.common.constant;

/** 内容审核结论，由敏感词等级与模型判定产出，决定内容直接公开还是转人工复核。 */
public enum ModerationStatus {

    /** 放行：模型明确 pass 且风险为 none/low 才给出，帖子按正常状态落库。 */
    ALLOW,

    /** 需复核：模型判定不确定、结果残缺或敏感词为中风险时使用，内容先停在待审。 */
    REVIEW,

    /** 高风险：敏感词表里的最高等级；命中后跳过模型直接转人工，不自动放行也不自动拒绝。 */
    BLOCK
}
