package com.hnist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 站内信发送入参（POST /api/messages）：兼容旧版只带 messageId 的回复调用。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {
    /** receiverId 为空时用 messageId 反查原消息的发送者作为接收人，两者都为空直接报错。 */
    private Integer receiverId;
    private Integer messageId;

    // 会话分类别名（private_letter / customer_service / consult，也接受分类 code），缺省按 community 处理。
    @Size(max = 30, message = "消息类型过长")
    private String type;

    @Size(max = 200, message = "标题不能超过200个字符")
    private String title;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息不能超过2000个字符")
    private String content;
}
