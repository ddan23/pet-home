package com.hnist.support;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/** AI SSE 事件输出器，把整段文本切成小块模拟打字机流式输出。 */
@Component
public class AiStreamEventWriter {

    /** 每个 delta 事件的字符数；块越小打字机效果越细，但事件数越多。 */
    private static final int CHUNK_SIZE = 10;
    /** 相邻两块之间的停顿毫秒数，控制前端渲染节奏。 */
    private static final long CHUNK_INTERVAL_MILLIS = 12L;

    /** 直接发送一个完整事件，IO 异常原样抛给调用方（通常意味着客户端已断开，由上层终止流程）。 */
    public void send(SseEmitter emitter, String event, Map<String, Object> data)
            throws IOException {
        emitter.send(SseEmitter.event()
                .name(event)
                .data(data, MediaType.APPLICATION_JSON));
    }

    /**
     * 把整段文本按 CHUNK_SIZE 切块、逐块以 delta 事件发出并线程停顿，模拟流式生成。
     * 空文本直接返回；被中断时恢复中断标志但不终止发送，避免前端拿到残缺内容。
     */
    public void streamText(SseEmitter emitter, String text) throws IOException {
        if (text == null || text.isEmpty()) {
            return;
        }
        int offset = 0;
        while (offset < text.length()) {
            int end = Math.min(offset + CHUNK_SIZE, text.length());
            send(emitter, "delta", Map.of("text", text.substring(offset, end)));
            offset = end;
            pause();
        }
    }

    private void pause() {
        try {
            Thread.sleep(CHUNK_INTERVAL_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
