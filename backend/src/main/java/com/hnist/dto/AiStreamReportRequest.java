package com.hnist.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 专属健康报告流式入参（POST /api/ai/health/report/stream，SSE）：没有提问文本，指令由服务端固定拼接。 */
@Data
public class AiStreamReportRequest {

    private Long conversationId;

    @NotNull(message = "宠物ID不能为空")
    private Integer petId;
}
