package com.hnist.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** 模型消息。content 可以是文本，也可以是图片理解所需的内容块。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiMessage {

    /** 取值 system、user、assistant、tool；角色只表达提示词位置，不参与任何权限判断。 */
    private String role;

    /** String 为纯文本，List<Map> 为内容块数组（文本 + image_url），由 multimodal 工厂生成。 */
    private Object content;

    /** 仅 role=tool 时有值，必须与发起调用的 tool_call id 对应，否则模型无法闭环。 */
    @JsonProperty("tool_call_id")
    private String toolCallId;

    /** assistant 消息回传的工具调用列表，参数为 JSON 字符串，由下游自行解析。 */
    @JsonProperty("tool_calls")
    private List<Map<String, Object>> toolCalls;

    public AiMessage(String role, Object content) {
        this.role = role;
        this.content = content;
    }

    public static AiMessage text(String role, String content) {
        return new AiMessage(role, content);
    }

    public static AiMessage multimodal(String role, String text, String imageUrl,
                                       String detail) {
        List<Map<String, Object>> blocks = List.of(
                Map.of("type", "text", "text", text == null ? "" : text),
                Map.of("type", "image_url", "image_url", Map.of(
                        "url", imageUrl,
                        "detail", detail == null ? "low" : detail
                ))
        );
        return new AiMessage(role, blocks);
    }

    public static AiMessage toolResult(String toolCallId, String content) {
        AiMessage message = new AiMessage("tool", content);
        message.setToolCallId(toolCallId);
        return message;
    }

    /** 只读纯文本：多模态消息的 content 是内容块列表，这里返回空串而不是抛错，调用方需自行取块。 */
    public String textContent() {
        return content instanceof String text ? text : "";
    }
}
