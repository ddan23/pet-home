package com.hnist.sse;

import com.hnist.entity.Notification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 通知实时推送服务：连接 30 分钟超时，25 秒心跳保活，超时与断开由回调自动清理。
 * notification 事件以通知 id 作为 SSE eventId，供客户端去重；其他事件不携带 id。
 */
@Service
public class NotificationSseService {

    private static final long TIMEOUT = 30 * 60 * 1000L;

    private final UserSseHub hub = new UserSseHub(TIMEOUT);

    public SseEmitter subscribe(Integer userId) {
        return hub.subscribe(userId);
    }

    public void push(Integer userId, Notification notification) {
        hub.pushEvent(userId, "notification", notification,
                String.valueOf(notification.getId()));
    }

    public void pushEvent(Integer userId, String eventName, Object payload) {
        hub.pushEvent(userId, eventName, payload, null);
    }

    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        hub.heartbeat();
    }
}
