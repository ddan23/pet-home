package com.hnist.service;

import com.hnist.domain.ModerationDecision;

/** 判定社区帖子、评论能否直接公开。 */
public interface ContentModerationService {

    /**
     * 契约：只返回 ALLOW 或 REVIEW，不返回 BLOCK——未通过的内容一律由人工裁决。
     * REVIEW 表示内容处于待审状态，调用方必须落库 pending 且不得展示给其他用户；ALLOW 才可置为正常状态。
     */
    ModerationDecision moderatePost(Integer userId, String title, String content,
                                    String imageUrl);

    ModerationDecision moderateComment(Integer userId, String content);

    // 无图场景的便捷重载：imageUrl 传 null，审核按纯文本执行，不触发图片待审分支。
    default ModerationDecision moderatePost(Integer userId, String title, String content) {
        return moderatePost(userId, title, content, null);
    }
}
