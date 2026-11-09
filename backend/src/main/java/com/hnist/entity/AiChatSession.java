package com.hnist.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** ai_chat_sessions 表行映射：AI 顾问会话，按 user_id、pet_id、mode 唯一。 */
@Data
public class AiChatSession {

    private Long id;

    private Integer userId;

    private Integer petId;

    /** QA、REPORT、RECOMMENDATION（AiChatMode） */
    private String mode;

    private LocalDateTime lastMessageAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
