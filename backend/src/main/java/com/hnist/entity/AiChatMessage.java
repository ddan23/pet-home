package com.hnist.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** ai_chat_messages 表行映射：AI 养宠顾问对话消息。 */
@Data
public class AiChatMessage {

    private Long id;

    private Long sessionId;

    /** user 或 assistant */
    private String role;

    /** TEXT、HEALTH_REPORT、RECOMMENDATION */
    private String messageType;

    private String content;

    /** JSON，引用来源 */
    private String citationsJson;

    /** JSON，推荐卡片等结构化数据 */
    private String structuredDataJson;

    /** 消息状态，默认 COMPLETED */
    private String status;

    private LocalDateTime createdAt;
}
