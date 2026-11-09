package com.hnist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 健康问答流式入参（POST /api/ai/health/chat/stream，SSE）：conversationId 为空时新建会话，id 由首个 start 事件回传。 */
@Data
public class AiStreamChatRequest {

    private Long conversationId;

    @NotNull(message = "宠物ID不能为空")
    private Integer petId;

    @NotBlank(message = "请输入健康问题")
    @Size(max = 1000, message = "问题不能超过1000个字符")
    private String message;
}
