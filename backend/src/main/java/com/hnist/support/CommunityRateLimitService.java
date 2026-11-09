package com.hnist.support;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** 单机内存社区限流：发帖 30 秒 1 次、评论每分钟 5 次，重启即清零，多实例各自计数。 */
@Service
public class CommunityRateLimitService {

    private static final Duration POST_WINDOW = Duration.ofSeconds(30);
    private static final int POST_LIMIT = 1;
    private static final Duration COMMENT_WINDOW = Duration.ofMinutes(1);
    private static final int COMMENT_LIMIT = 5;
    /** 清理时保留的时间戳跨度，需大于所有业务窗口，避免误删仍在窗口内的记录。 */
    private static final Duration RETENTION = Duration.ofMinutes(10);

    private final KeyedSlidingWindowLimiter postLimiter = new KeyedSlidingWindowLimiter();
    private final KeyedSlidingWindowLimiter commentLimiter = new KeyedSlidingWindowLimiter();

    public void checkPost(Integer userId) {
        postLimiter.check(userId, POST_LIMIT, POST_WINDOW,
                "发帖过于频繁，请稍后再试");
    }

    public void checkComment(Integer userId) {
        commentLimiter.check(userId, COMMENT_LIMIT, COMMENT_WINDOW,
                "评论过于频繁，请稍后再试");
    }

    /** 每 5 分钟清理已过期的窗口时间戳，防止 Map 中用户队列无限增长。 */
    @Scheduled(fixedDelay = 300_000L)
    public void cleanupExpiredWindows() {
        postLimiter.cleanup(RETENTION);
        commentLimiter.cleanup(RETENTION);
    }
}
