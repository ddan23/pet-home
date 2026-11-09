package com.hnist.sse;

import com.hnist.entity.Message;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 会话消息实时推送服务：连接 30 分钟超时，25 秒心跳保活，超时与断开由回调自动清理。
 * Message 推送以消息 id 作为 SSE eventId，供客户端去重。
 */
@Service
public class ConversationSseService {

    private static final long TIMEOUT = 30 * 60 * 1000L;

    private final UserSseHub hub = new UserSseHub(TIMEOUT);

    public SseEmitter subscribe(Integer userId) {
        return hub.subscribe(userId);
    }

    public void push(Integer userId, String eventName, Object payload) {
        String eventId = payload instanceof Message message
                ? String.valueOf(message.getId()) : null;
        hub.pushEvent(userId, eventName, payload, eventId);
    }

    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        hub.heartbeat();
    }
}
