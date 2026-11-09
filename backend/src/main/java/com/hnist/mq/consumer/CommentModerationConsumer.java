package com.hnist.mq.consumer;

import com.hnist.job.CommentModerationWorker;
import com.hnist.mq.config.RabbitMqConfig;
import com.hnist.mq.message.CommentModerationMessage;

import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 评论审核消息消费者：从持久化审核队列取出评论 id，执行敏感词过滤与 AI 审核。
 * 幂等性：CommentModerationWorker.moderate 只处理 status = pending 的评论，
 * 重复投递、人工已复核、多实例并发消费都会被状态检查与条件更新挡住，不会重复计数或重复通知。
 */
@Slf4j
@Component
public class CommentModerationConsumer {

    private final CommentModerationWorker moderationWorker;

    public CommentModerationConsumer(CommentModerationWorker moderationWorker) {
        this.moderationWorker = moderationWorker;
    }

    /**
     * 消费审核消息：AI 审核失败直接转人工复核而不是 nack 重试——
     * 模型故障期间重试只会重复失败，转人工让评论仍能进入复核队列，不会永远卡在 pending。
     */
    @RabbitListener(queues = RabbitMqConfig.COMMENT_MODERATION_QUEUE)
    public void onCommentCreated(CommentModerationMessage message) {
        try {
            moderationWorker.moderate(message.commentId());
        } catch (RuntimeException exception) {
            log.warn("评论 MQ 审核失败，转入人工复核，commentId={}",
                    message.commentId(), exception);
            moderationWorker.fallbackToReview(message.commentId());
        }
    }
}
