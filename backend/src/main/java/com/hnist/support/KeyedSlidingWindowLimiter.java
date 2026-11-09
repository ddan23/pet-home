package com.hnist.support;

import com.hnist.common.exception.BusinessException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按用户维护滑动窗口请求时间戳的限流器：真实滑窗（非固定窗口），窗口内达到上限即拒绝。
 * requests 为 ConcurrentHashMap，每个用户队列用 synchronized 保护，全局只锁单个用户不影响他人；
 * 队列会随时间增长，需调用方定期执行 cleanup。
 */
public final class KeyedSlidingWindowLimiter {

    private final Map<Integer, Deque<Instant>> requests = new ConcurrentHashMap<>();

    /** 超限时直接抛 {@link BusinessException}(429, message)，供无需区分返回值的调用方使用。 */
    public void check(Integer userId, int limit, Duration window, String message) {
        if (!tryAcquire(userId, limit, window)) {
            throw new BusinessException(429, message);
        }
    }

    /** 尝试占一个窗口名额：过期时间戳先出队再判断容量，成功则记录当前时刻。 */
    public boolean tryAcquire(Integer userId, int limit, Duration window) {
        Instant now = Instant.now();
        Deque<Instant> timestamps = requests.computeIfAbsent(userId,
                key -> new ArrayDeque<>());
        synchronized (timestamps) {
            removeExpired(timestamps, now.minus(window));
            if (timestamps.size() >= limit) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    /** 清空 retention 之前的过期时间戳并移除空队列，避免长期运行后内存泄漏。 */
    public void cleanup(Duration retention) {
        Instant threshold = Instant.now().minus(retention);
        requests.forEach((userId, timestamps) -> {
            synchronized (timestamps) {
                removeExpired(timestamps, threshold);
                if (timestamps.isEmpty()) {
                    requests.remove(userId, timestamps);
                }
            }
        });
    }

    private void removeExpired(Deque<Instant> timestamps, Instant threshold) {
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(threshold)) {
            timestamps.removeFirst();
        }
    }
}
