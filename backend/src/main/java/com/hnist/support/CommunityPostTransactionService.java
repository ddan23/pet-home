package com.hnist.support;

import com.hnist.domain.ModerationDecision;
import com.hnist.mapper.PostMapper;
import com.hnist.entity.Post;
import com.hnist.service.ModerationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 帖子及审核日志的事务持久化。 */
@Service
public class CommunityPostTransactionService {

    private final PostMapper postMapper;
    private final ModerationLogService moderationLogService;

    public CommunityPostTransactionService(PostMapper postMapper,
                                           ModerationLogService moderationLogService) {
        this.postMapper = postMapper;
        this.moderationLogService = moderationLogService;
    }

    /**
     * 帖子插入与审核日志在同一事务内落库：日志写入失败会连带回滚发帖，
     * 保证「不留下没有审核记录的公开帖子」。
     */
    @Transactional(rollbackFor = Exception.class)
    public Post saveWithModerationLog(
            Post post, ModerationDecision decision, String triggerSource,
            String ruleHits, String targetExcerpt, Long latencyMs) {
        postMapper.insert(post);
        moderationLogService.record(post.getUserId(), "POST", post.getId().longValue(),
                decision, triggerSource, ruleHits, targetExcerpt, latencyMs);
        return post;
    }
}
