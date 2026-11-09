package com.hnist.service.impl;

import com.hnist.service.KnowledgeIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** 知识库索引异步同步服务，避免向量化阻塞管理端保存操作。 */
@Slf4j
@Service
public class KnowledgeIndexSyncService {

    private final KnowledgeIndexService knowledgeIndexService;

    public KnowledgeIndexSyncService(KnowledgeIndexService knowledgeIndexService) {
        this.knowledgeIndexService = knowledgeIndexService;
    }

    /** 投递到 knowledgeIndexExecutor 异步执行，管理端保存不必等向量化；失败只记日志不外抛，索引缺口靠 rebuildAll 或下次保存补齐。 */
    @Async("knowledgeIndexExecutor")
    public void syncAsync(Long articleId) {
        try {
            knowledgeIndexService.syncArticle(articleId);
        } catch (RuntimeException exception) {
            log.warn("知识库索引同步失败，articleId={}", articleId, exception);
        }
    }
}
