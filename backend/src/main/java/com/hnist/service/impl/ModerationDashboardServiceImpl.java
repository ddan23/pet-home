package com.hnist.service.impl;

import com.hnist.common.constant.PostStatus;
import com.hnist.vo.ModerationUnreadSummaryResponse;
import com.hnist.mapper.PostCommentMapper;
import com.hnist.mapper.PostMapper;
import com.hnist.service.ModerationDashboardService;
import org.springframework.stereotype.Service;

/** 汇总后台审核待办数量（待审帖子、待审评论、合计）。 */
@Service
public class ModerationDashboardServiceImpl implements ModerationDashboardService {

    private final PostMapper postMapper;
    private final PostCommentMapper commentMapper;

    public ModerationDashboardServiceImpl(PostMapper postMapper,
                                          PostCommentMapper commentMapper) {
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
    }

    @Override
    public ModerationUnreadSummaryResponse getUnreadSummary() {
        // 待办口径取业务表而不是 content_moderation_logs：posts.status = 'pending' 且 deleted_at IS NULL，
        // 同一内容被多轮审核时日志会有多行，按日志计数会把待办重复放大。category/keyword 传 null 表示不限分类、不筛关键字。
        int posts = postMapper.countByCategory(
                null, null, PostStatus.PENDING.getCode());
        // 评论口径是 post_comments.status = 'pending' 且评论与所属帖子都未软删：父帖已删的评论不进入待审，避免管理员处理找不到入口的脏待办。
        int comments = commentMapper.countPending();
        // 合计直接相加，不额外查一次全量：两个计数口径互不重叠（帖子/评论分表），相加即为真实待办总量。
        return new ModerationUnreadSummaryResponse(posts, comments, posts + comments);
    }
}
