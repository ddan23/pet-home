package com.hnist.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/** 社区异步审核与知识索引线程池；审核现走 RabbitMQ 消费者，本池仅作 MQ 故障时的降级通道，索引任务仍由本类承载。 */
@EnableAsync
@Configuration
public class AsyncConfig {

    /** 审核降级通道：MQ 不可用时本地兜底审核，核心 2、最大 4、队列 200；队列满用 AbortPolicy 直接报错，不回落到请求线程执行审核。 */
    @Bean(name = "moderationExecutor")
    public Executor moderationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("moderation-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 停机时等待在途任务（审核 10s、索引 20s 的差异在下个 Bean）：正在写的审核结论不能被硬中断。
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /** 索引任务：核心 1、最大 2、队列 200；队列满用 CallerRunsPolicy 回落到调用线程，宁可拖慢保存也不丢索引。 */
    @Bean(name = "knowledgeIndexExecutor")
    public Executor knowledgeIndexExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("knowledge-index-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
