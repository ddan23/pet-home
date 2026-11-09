package com.hnist.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 健康助手历史消息：作为对话上下文回放给模型，不含账号信息。 */
@Data
public class HealthChatMessage {

    /** 历史消息角色，仅用于拼提示词顺序，不参与权限判断。 */
    @NotBlank(message = "消息角色不能为空")
    private String role;

    /** 单条上限 1000 字符：历史整体会随每次请求重发，超长会挤占证据与提示词的 token 预算。 */
    @NotBlank(message = "历史消息内容不能为空")
    @Size(max = 1000, message = "单条历史消息不能超过1000个字符")
    private String content;
}
