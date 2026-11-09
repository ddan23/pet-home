package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 管理员社区审核待办汇总，取业务表状态而非审核日志条数，避免多轮审核重复计数。 */
@Data
@AllArgsConstructor
public class ModerationUnreadSummaryResponse {

    /** status=pending 且未软删的帖子数。 */
    private Integer posts;

    /** status=pending 且评论与所属帖子都未软删的评论数。 */
    private Integer comments;

    /** 直接相加：帖子与评论分表，两个口径互不重叠。 */
    private Integer total;
}
