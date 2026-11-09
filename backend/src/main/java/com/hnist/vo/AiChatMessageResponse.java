package com.hnist.vo;

import com.hnist.domain.HealthCitation;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** AI 会话历史接口的单条消息：由 ai_chat_messages 逐条转出，供前端恢复对话上下文。 */
@Data
@AllArgsConstructor
public class AiChatMessageResponse {

    private Long id;

    private String role;

    private String messageType;

    private String content;

    /** 知识库引用编号（S1、S2…）；库中 JSON 反序列化失败时降级为空列表，不连累整个会话加载。 */
    private List<HealthCitation> citations;

    /** 消息附带的业务快照（如推荐结果）；JSON 为空或损坏时给空 Map 而不是 null。 */
    private Map<String, Object> structuredData;

    /** 消息落库时间，即数据库时间，不做时区换算。 */
    private LocalDateTime createdAt;
}
