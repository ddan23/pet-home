package com.hnist.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** AI 流式响应线程池：模型调用要几十秒，生成放这里执行，Web 线程只负责立即返回 SseEmitter。 */
@Configuration
public class AiStreamConfig {

    /** 核心 2、最大 4、队列 20；队列满后按默认 AbortPolicy 拒绝，避免任务无限堆积把内存吃满。 */
    @Bean(name = "aiStreamExecutor")
    public Executor aiStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("ai-stream-");
        executor.initialize();
        return executor;
    }
}
