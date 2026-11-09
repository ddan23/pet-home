package com.hnist.service;

import com.hnist.vo.ModerationUnreadSummaryResponse;

/** 管理员社区审核待办汇总服务。 */
public interface ModerationDashboardService {

    /**
     * 待办按业务表口径统计：posts.status='pending' 且未软删，post_comments.status='pending' 且评论与父帖都未软删。
     * 不按 content_moderation_logs 行数累加，同一内容多轮审核不会被重复计数；total 直接为两项之和。
     */
    ModerationUnreadSummaryResponse getUnreadSummary();
}
