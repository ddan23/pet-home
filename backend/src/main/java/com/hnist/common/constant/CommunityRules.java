package com.hnist.common.constant;

/** 社区统一上限，DTO 校验与服务端截断共用同一组常量，避免前后台各写一份数字。 */
public final class CommunityRules {

    /** 评论正文最大字符数：DTO 的 @Size 与服务层长度校验同源，超长直接拒绝而不截断。 */
    public static final int COMMENT_MAX_LENGTH = 500;
    /** 社区列表分页上限：作为 pageSize 上限传入 PageUtils，挡住超大页查询。 */
    public static final int COMMENT_PAGE_SIZE = 50;
    /** 帖子摘要最大字符数：写审核日志 target_excerpt 前按该长度截断，避免日志表存整篇正文。 */
    public static final int POST_EXCERPT_MAX_LENGTH = 1000;

    private CommunityRules() {
    }
}
