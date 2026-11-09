package com.hnist.support;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** 单机内存私信限流：每用户每分钟最多 15 条，重启即清零，多实例各自计数。 */
@Service
public class ConversationRateLimitService {

    private static final Duration MESSAGE_WINDOW = Duration.ofMinutes(1);
    private static final int MESSAGE_LIMIT = 15;
    /** 清理时保留的时间戳跨度，需大于消息窗口，避免误删仍在窗口内的记录。 */
    private static final Duration RETENTION = Duration.ofMinutes(10);

    private final KeyedSlidingWindowLimiter messageLimiter = new KeyedSlidingWindowLimiter();

    public void checkMessage(Integer userId) {
        messageLimiter.check(userId, MESSAGE_LIMIT, MESSAGE_WINDOW,
                "消息发送过于频繁，请稍后再试");
    }

    /** 每 5 分钟清理已过期的窗口时间戳，防止 Map 中用户队列无限增长。 */
    @Scheduled(fixedDelay = 300_000L)
    public void cleanupExpiredWindows() {
        messageLimiter.cleanup(RETENTION);
    }
}
