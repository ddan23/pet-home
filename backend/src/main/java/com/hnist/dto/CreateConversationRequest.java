package com.hnist.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 创建或复用一对一会话：同一对用户 + 同一分类已有会话时直接返回，type 必须与 category 成对，落库值由分类派生。 */
@Data
public class CreateConversationRequest {

    @NotNull(message = "会话对象不能为空")
    private Integer targetUserId;

    @Pattern(regexp = "community|mall|service", message = "会话分类不合法")
    private String category = "community";

    @Pattern(regexp = "private_letter|customer_service|consult", message = "会话类型不合法")
    private String type = "private_letter";
}
