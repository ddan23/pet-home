package com.hnist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 商品与服务推荐流式入参（POST /api/ai/health/recommend/stream，SSE）：conversationId 为空即新建会话。 */
@Data
public class AiStreamRecommendationRequest {

    private Long conversationId;

    @NotNull(message = "宠物ID不能为空")
    private Integer petId;

    @NotBlank(message = "请描述推荐需求")
    @Size(max = 1000, message = "需求不能超过1000个字符")
    private String message;
}
