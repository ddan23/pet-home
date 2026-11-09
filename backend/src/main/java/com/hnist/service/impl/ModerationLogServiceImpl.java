package com.hnist.service.impl;

import com.hnist.domain.ModerationDecision;
import com.hnist.common.constant.ModerationStatus;
import com.hnist.common.constant.ModerationReviewStatus;
import com.hnist.mapper.ContentModerationLogMapper;
import com.hnist.entity.ContentModerationLog;
import com.hnist.service.ModerationLogService;
import org.springframework.stereotype.Service;

/** 把每次审核结论写入 content_moderation_logs 供人工复核和后台统计。 */
@Service
public class ModerationLogServiceImpl implements ModerationLogService {

    private final ContentModerationLogMapper moderationLogMapper;

    public ModerationLogServiceImpl(ContentModerationLogMapper moderationLogMapper) {
        this.moderationLogMapper = moderationLogMapper;
    }

    @Override
    public void record(Integer userId, String targetType, Long targetId,
                       ModerationDecision decision) {
        // 未传来源的调用方按 AI 记账，与表上 trigger_source 的默认值 'AI' 保持一致，保证按来源筛选时不会出现空值。
        record(userId, targetType, targetId, decision, "AI", null, null, null);
    }

    @Override
    public void record(Integer userId, String targetType, Long targetId,
                       ModerationDecision decision, String triggerSource, String ruleHits,
                       String targetExcerpt, Long latencyMs) {
        ContentModerationLog log = new ContentModerationLog();
        log.setUserId(userId);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setTriggerSource(triggerSource);
        log.setRuleHits(ruleHits);
        // target_excerpt 对应 varchar(1000) 的摘要列，由调用方截断后传入：留够回溯判定，避免正文在日志表里再存一份全文。
        log.setTargetExcerpt(targetExcerpt);
        log.setLatencyMs(latencyMs);
        // ai_decision 存的是机器结论（ALLOW/REVIEW/BLOCK），与 review_status 的人工进度是两个维度，不能互相替代。
        log.setAiDecision(decision.status().name());
        log.setRiskLevel(decision.riskLevel());
        log.setCategories(decision.categories());
        log.setImageUrl(decision.imageUrl());
        // 同时记内容哈希：复核时可用它确认被判定的是否为同一张图，不必依赖易变的图片地址。
        log.setImageHash(decision.imageHash());
        log.setEvidenceJson(decision.evidence());
        log.setConfidence(decision.confidence());
        log.setReason(decision.reason());
        log.setModelName(decision.modelName());
        log.setPromptVersion(decision.promptVersion());
        // REVIEW 才进人工队列（pending），ALLOW 直接记 auto_completed：这个字段表示人工复核进度，
        // 判错会让后台把等着人审的内容当成已处理，或把自动通过的记录又塞回复核列表。
        log.setReviewStatus(decision.status() == ModerationStatus.REVIEW
                ? ModerationReviewStatus.PENDING.getCode()
                : ModerationReviewStatus.AUTO_COMPLETED.getCode());
        moderationLogMapper.insert(log);
    }

    @Override
    public com.hnist.entity.ContentModerationLog findLatest(String targetType, Long targetId) {
        // 同一目标可能被多次审核（自动 + 复核），按 created_at DESC, id DESC 取一条，下游通知文案以最新结论为准。
        return moderationLogMapper.selectLatest(targetType, targetId);
    }

    @Override
    public void review(String targetType, Long targetId, Integer reviewerId,
                       String reviewStatus) {
        // WHERE 带 review_status = 'pending'：已 auto_completed 或已被其他管理员处理过的行不会被覆盖，
        // 并发复核时只有第一个请求生效；reviewer_id 与 reviewed_at 同语句写入留痕，走的是 idx_moderation_review 索引。
        moderationLogMapper.updateReviewStatus(targetType, targetId, reviewerId, reviewStatus);
    }
}
