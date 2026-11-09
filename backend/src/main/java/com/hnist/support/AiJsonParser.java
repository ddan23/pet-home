package com.hnist.support;

import com.hnist.common.exception.AiUnavailableException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** AI 返回内容中的 JSON 提取与解析。 */
@Component
public class AiJsonParser {

    private final ObjectMapper objectMapper;

    public AiJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 从大模型返回的自由文本中提取首个 JSON 对象。
     * 输入假设：可能带 Markdown 代码围栏或前后说明文字，靠大括号定位真正的 JSON 体；
     * 找不到成对大括号或内容非法时抛 {@link AiUnavailableException}，调用方据此向用户返回「AI 暂不可用」。
     */
    public JsonNode parse(String content) {
        String json = content == null ? "" : content.trim();
        if (json.startsWith("```")) {
            int firstLineBreak = json.indexOf('\n');
            int lastFence = json.lastIndexOf("```");
            if (firstLineBreak >= 0 && lastFence > firstLineBreak) {
                json = json.substring(firstLineBreak + 1, lastFence).trim();
            }
        }
        int firstBrace = json.indexOf('{');
        int lastBrace = json.lastIndexOf('}');
        if (firstBrace < 0 || lastBrace <= firstBrace) {
            throw new AiUnavailableException("AI 返回格式不正确");
        }
        try {
            return objectMapper.readTree(json.substring(firstBrace, lastBrace + 1));
        } catch (JsonProcessingException exception) {
            throw new AiUnavailableException("AI 返回格式不正确", exception);
        }
    }
}
