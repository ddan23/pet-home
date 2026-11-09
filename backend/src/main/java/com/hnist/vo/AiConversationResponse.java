package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 会话列表项：由 ai_chat_sessions 直接投影，列表按 lastMessageAt 倒序展示。 */
@Data
@AllArgsConstructor
public class AiConversationResponse {

    private Long id;

    private Integer petId;

    private String mode;

    /** 最后一条消息时间，由 SQL NOW() 更新，多实例部署下以数据库时钟为准。 */
    private LocalDateTime lastMessageAt;
}
