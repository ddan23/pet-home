package com.hnist.service;

import com.hnist.domain.ModerationDecision;
import com.hnist.entity.ContentModerationLog;

/** 内容审核结论的留痕与人工复核状态回写。 */
public interface ModerationLogService {

    /** 未指定来源的调用按 triggerSource=AI 记账，与表上默认值一致；规则命中、摘要与耗时留空。 */
    void record(Integer userId, String targetType, Long targetId, ModerationDecision decision);

    /**
     * 每次审核只追加一行，不改写历史结论；review_status 随即初始化：REVIEW 进 pending 人工队列，其余记 auto_completed。
     * targetExcerpt 对应 varchar(1000)，由调用方截断后传入，日志表不存正文全文。
     */
    void record(Integer userId, String targetType, Long targetId,
                ModerationDecision decision, String triggerSource, String ruleHits,
                String targetExcerpt, Long latencyMs);

    /** 同一目标可能经历多轮审核，按创建时间倒序取最新一条；没有记录返回 null。 */
    ContentModerationLog findLatest(String targetType, Long targetId);

    /** 条件更新只作用于 review_status='pending' 的行：并发复核只有第一个生效，已 auto_completed 或已处理的记录不会被覆盖。 */
    void review(String targetType, Long targetId, Integer reviewerId,
                String reviewStatus);
}
