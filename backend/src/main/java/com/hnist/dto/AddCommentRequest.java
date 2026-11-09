package com.hnist.dto;

import com.hnist.common.constant.CommunityRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 帖子评论 / 回复提交入参，由 POST /api/posts/{id}/comments 接收。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddCommentRequest {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = CommunityRules.COMMENT_MAX_LENGTH,
            message = "评论不能超过" + CommunityRules.COMMENT_MAX_LENGTH + "个字符")
    private String content;

    /** 非空表示回复某条评论：服务端校验父评论属于本帖且状态为 normal，楼层固定两层。 */
    private Long parentId;
}
