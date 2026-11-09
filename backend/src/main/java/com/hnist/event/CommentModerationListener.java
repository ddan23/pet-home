package com.hnist.event;

import com.hnist.job.CommentModerationWorker;

import com.hnist.mq.config.RabbitMqConfig;
import com.hnist.mq.message.CommentModerationMessage;

import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;

/**
 * 评论事务提交后将审核任务投递到 RabbitMQ 审核队列。
 * AFTER_COMMIT：发布方事务提交成功才触发，回滚的评论不会被审核；
 * fallbackExecution = true：发布方没有事务上下文时也照常执行，事件不丢；
 * MQ 不可用时降级为本地线程池审核（moderationExecutor），中间件故障期间审核链路仍可用。
 */
@Slf4j
@Component
public class CommentModerationListener {

    private final RabbitTemplate rabbitTemplate;
    private final CommentModerationWorker moderationWorker;

    /** 仅作 MQ 故障降级通道：正常路径审核在 MQ 消费者线程执行，不占用请求线程。 */
    private final Executor moderationExecutor;

    public CommentModerationListener(RabbitTemplate rabbitTemplate,
                                     CommentModerationWorker moderationWorker,
                                     @Qualifier("moderationExecutor") Executor moderationExecutor) {
        this.rabbitTemplate = rabbitTemplate;
        this.moderationWorker = moderationWorker;
        this.moderationExecutor = moderationExecutor;
    }

    /**
     * 优先投递 MQ：消息持久化在 broker，审核高峰排队与应用重启都不丢任务；
     * 发送失败降级本地线程池直接审核，降级审核异常时同样转人工复核，评论不会永远卡在 pending。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void handle(CommentModerationEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitMqConfig.COMMENT_MODERATION_EXCHANGE,
                    RabbitMqConfig.COMMENT_MODERATION_ROUTING_KEY,
                    new CommentModerationMessage(event.commentId()));
        } catch (RuntimeException exception) {
            log.warn("评论审核消息发送失败，降级为本地线程池审核，commentId={}",
                    event.commentId(), exception);
            moderationExecutor.execute(() -> moderateWithFallback(event.commentId()));
        }
    }

    /** 本地降级审核：AI 审核异常时转人工复核，与 MQ 消费者的兜底行为保持一致。 */
    private void moderateWithFallback(Long commentId) {
        try {
            moderationWorker.moderate(commentId);
        } catch (RuntimeException exception) {
            log.warn("评论降级审核失败，转入人工复核，commentId={}", commentId, exception);
            moderationWorker.fallbackToReview(commentId);
        }
    }
}
