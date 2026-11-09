package com.hnist.support;

import com.hnist.config.RagProperties;
import com.hnist.service.KnowledgeIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 启动时初始化知识库向量索引，失败不影响应用启动。 */
@Slf4j
@Component
public class KnowledgeIndexBootstrap implements ApplicationRunner {

    private final RagProperties ragProperties;

    private final KnowledgeIndexService knowledgeIndexService;

    public KnowledgeIndexBootstrap(RagProperties ragProperties,
                                   KnowledgeIndexService knowledgeIndexService) {
        this.ragProperties = ragProperties;
        this.knowledgeIndexService = knowledgeIndexService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!ragProperties.isReady()) {
            log.info("向量检索未启用，知识库索引初始化跳过");
            return;
        }
        long startedAt = System.currentTimeMillis();
        try {
            int synced = knowledgeIndexService.rebuildAll();
            log.info("知识库索引初始化完成，文章数={}，耗时={}ms",
                    synced, System.currentTimeMillis() - startedAt);
        } catch (RuntimeException exception) {
            log.warn("知识库索引初始化失败，检索将降级为 MySQL 全文", exception);
        }
    }
}
