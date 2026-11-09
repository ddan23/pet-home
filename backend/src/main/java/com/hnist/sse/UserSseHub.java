package com.hnist.sse;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 按用户维护 SSE 连接、事件推送与心跳。
 * 同一用户允许多条并发连接（CopyOnWriteArrayList），回调清理与发送失败移除都依赖它保证并发安全。
 */
public final class UserSseHub {

    private final long timeoutMillis;
    private final Map<Integer, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public UserSseHub(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    /** 注册后立即发送 connected 事件确认链路；完成、超时、错误、首发失败任一路径都会触发清理。 */
    public SseEmitter subscribe(Integer userId) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        emitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("connected")
                    .data("connected", MediaType.TEXT_PLAIN));
        } catch (IOException exception) {
            cleanup.run();
        }
        return emitter;
    }

    /** 遍历该用户全部连接逐个发送；单条连接写失败只移除自身，不影响其他连接。eventId 供客户端标识事件。 */
    public void pushEvent(Integer userId, String eventName, Object payload, String eventId) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) {
            return;
        }
        for (SseEmitter emitter : userEmitters) {
            try {
                SseEmitter.SseEventBuilder event = SseEmitter.event().name(eventName);
                if (eventId != null) {
                    event.id(eventId);
                }
                emitter.send(event.data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException exception) {
                remove(userId, emitter);
            }
        }
    }

    /** 向所有存活连接发送心跳保活，不携带业务数据；发送失败即移除死连接。 */
    public void heartbeat() {
        emitters.forEach((userId, userEmitters) -> {
            for (SseEmitter emitter : userEmitters) {
                try {
                    emitter.send(SseEmitter.event().name("heartbeat")
                            .data("ping", MediaType.TEXT_PLAIN));
                } catch (IOException | IllegalStateException exception) {
                    remove(userId, emitter);
                }
            }
        });
    }

    /** 移除单条连接；该用户名下已无连接时连键一并清掉，避免 map 无限膨胀。 */
    private void remove(Integer userId, SseEmitter emitter) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }
}
