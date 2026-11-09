package com.hnist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 会话消息发送请求（POST /api/conversations/{id}/messages）：同一 clientMessageId 重发只落一条。 */
@Data
public class SendConversationMessageRequest {

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息不能超过2000个字符")
    private String content;

    // 客户端生成的幂等键，受 uk_message_client 唯一索引约束：并发重试时冲突方回查已有消息返回，不报错也不重复入库。
    @NotBlank(message = "客户端消息ID不能为空")
    @Size(max = 64, message = "客户端消息ID不能超过64个字符")
    private String clientMessageId;

    @Pattern(regexp = "text|image|product_card|service_card|order_card",
            message = "消息内容类型不合法")
    private String contentType = "text";

    @Size(max = 30, message = "关联类型过长")
    private String relatedType;

    private Long relatedId;
}
