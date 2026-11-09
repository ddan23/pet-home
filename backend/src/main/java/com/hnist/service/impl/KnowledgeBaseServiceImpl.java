package com.hnist.service.impl;

import com.hnist.common.exception.BusinessException;
import com.hnist.common.constant.KnowledgeStatus;
import com.hnist.vo.KnowledgeArticleDetailResponse;
import com.hnist.dto.SaveKnowledgeArticleRequest;
import com.hnist.mapper.KnowledgeMapper;
import com.hnist.entity.KnowledgeArticle;
import com.hnist.entity.KnowledgeChunk;
import com.hnist.common.result.PageResult;
import com.hnist.service.KnowledgeBaseService;
import com.hnist.common.util.PageUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 健康知识文章的管理端读写：保存正文分块、发布审核状态并触发向量索引同步。 */
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    // 单一分块的字符上限，决定 knowledge_chunks 的行切法：调大调小都会让同一篇文章切出不同数量、不同边界的分块，
    // 已入库的 chunk_id=a{articleId}_c{chunkOrder} 与向量随之后错位，所以改这个值必须配合 rebuildAll 重建。
    private static final int MAX_CHUNK_LENGTH = 600;
    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeIndexSyncService knowledgeIndexSyncService;

    public KnowledgeBaseServiceImpl(KnowledgeMapper knowledgeMapper,
                                    KnowledgeIndexSyncService knowledgeIndexSyncService) {
        this.knowledgeMapper = knowledgeMapper;
        this.knowledgeIndexSyncService = knowledgeIndexSyncService;
    }

    @Override
    public PageResult<KnowledgeArticle> list(String keyword, String status,
                                             int page, int pageSize) {
        // 页码与页大小先夹到合法区间（page>=1、pageSize<=100）：否则 offset 可能为负，管理端传 0 会直接报错。
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(pageSize);
        List<KnowledgeArticle> list = knowledgeMapper.selectAll(keyword, status,
                (safePage - 1) * safePageSize, safePageSize);
        return new PageResult<>(list, knowledgeMapper.countAll(keyword, status),
                safePage, safePageSize);
    }

    @Override
    public KnowledgeArticleDetailResponse detail(Long id) {
        KnowledgeArticle article = requireArticle(id);
        return new KnowledgeArticleDetailResponse(article,
                knowledgeMapper.selectChunksByArticleId(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeArticle save(Long id, SaveKnowledgeArticleRequest request,
                                 Integer reviewerId) {
        KnowledgeArticle article = id == null ? new KnowledgeArticle() : requireArticle(id);
        article.setTitle(request.getTitle().trim());
        article.setSpecies(request.getSpecies());
        article.setTopic(request.getTopic().trim());
        article.setSummary(request.getSummary().trim());
        article.setSourceTitle(request.getSourceTitle().trim());
        article.setSourceUrl(request.getSourceUrl().trim());
        article.setPublisher(request.getPublisher().trim());
        article.setPublishedOn(request.getPublishedOn());
        article.setReviewStatus(request.getReviewStatus());
        // 审核人和审核日期只在 published 时落库，草稿/归档一律置空：检索侧把 reviewed_on 当作已审核凭证，
        // 留着旧值会让退回编辑的内容继续显示为「已审核来源」。
        boolean published = KnowledgeStatus.PUBLISHED.getCode()
                .equals(request.getReviewStatus());
        article.setReviewedOn(published
                ? LocalDate.now() : null);
        article.setReviewerId(published
                ? reviewerId : null);

        if (id == null) {
            knowledgeMapper.insertArticle(article);
        } else {
            // 条件更新影响 0 行说明文章已被并发删除，直接抛 404 终止事务，避免给不存在的文章写分块。
            if (knowledgeMapper.updateArticle(article) == 0) {
                throw new BusinessException(404, "知识文章不存在");
            }
            // 更新正文前必须整篇删旧分块：改短后分块数变少，只追加会残留旧 chunk_order，
            // 检索仍可能召回已被删除的段落，且与 Milvus 里的旧向量对不上。
            knowledgeMapper.deleteChunks(id);
        }
        saveChunks(article.getId(), request.getContent());
        // 向量化是外部 HTTP + Milvus 写入，放在异步服务里执行，管理端保存不被拖慢；
        // 同步失败只记日志不回滚 MySQL，索引缺口靠 rebuildAll 或下次保存补齐。
        knowledgeIndexSyncService.syncAsync(article.getId());
        return article;
    }

    @Override
    public void publish(Long id, Integer reviewerId) {
        requireArticle(id);
        // 没有正文分块的文章不能被检索命中，发布前拦住：否则会上线一篇永远召回不到的空内容。
        if (knowledgeMapper.selectChunksByArticleId(id).isEmpty()) {
            throw new BusinessException("知识文章没有正文分块，不能发布");
        }
        knowledgeMapper.updateReviewStatus(
                id, KnowledgeStatus.PUBLISHED.getCode(), reviewerId);
        knowledgeIndexSyncService.syncAsync(id);
    }

    @Override
    public void archive(Long id, Integer reviewerId) {
        requireArticle(id);
        // 归档后 syncArticle 会判定非 published 并删除该文章向量，检索侧立即下线，无需手工清理索引。
        knowledgeMapper.updateReviewStatus(
                id, KnowledgeStatus.ARCHIVED.getCode(), reviewerId);
        knowledgeIndexSyncService.syncAsync(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireArticle(id);
        // 分块由外键 ON DELETE CASCADE 随文章一并删除；删除后 syncArticle 查不到文章，只能靠 removeArticle
        // 按 articleId 清理 Milvus 残留向量，所以这里仍要传 id 触发同步。
        knowledgeMapper.deleteArticle(id);
        knowledgeIndexSyncService.syncAsync(id);
    }

    private KnowledgeArticle requireArticle(Long id) {
        KnowledgeArticle article = knowledgeMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(404, "知识文章不存在");
        }
        return article;
    }

    private void saveChunks(Long articleId, String content) {
        List<String> chunks = splitContent(content);
        // chunk_order 直接用列表下标：它是 Milvus 里 chunk_id=a{articleId}_c{chunkOrder} 的组成部分，
        // 必须保持连续且从 0 开始，否则同一篇文章重建索引会生成与旧向量不重合的主键。
        for (int index = 0; index < chunks.size(); index++) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setArticleId(articleId);
            chunk.setChunkOrder(index);
            chunk.setContent(chunks.get(index));
            knowledgeMapper.insertChunk(chunk);
        }
    }

    private List<String> splitContent(String content) {
        // 统一换行为 \n：不同客户端粘贴的正文混用 \r\n 与 \r，不先归一化按空行分段会切出不同分块。
        String normalized = content == null ? "" : content.trim()
                .replace("\r\n", "\n").replace('\r', '\n');
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        // 切分口径：优先按空行（段落边界）合并，单段超过 MAX_CHUNK_LENGTH 才按字符硬切，且相邻块不重叠；
        // 段落优先是为了让一块尽量只讲一件事，切得太碎会让检索命中的片段缺少上下文。
        for (String paragraph : normalized.split("\\n\\s*\\n")) {
            String cleaned = paragraph.trim();
            if (cleaned.isBlank()) {
                continue;
            }
            if (current.length() > 0
                    && current.length() + cleaned.length() + 1 > MAX_CHUNK_LENGTH) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            int offset = 0;
            // 超长段落按 MAX_CHUNK_LENGTH 顺序推进，不回溯重叠区：重叠会让同一段文字出现在多个 chunk_id 下，
            // 检索时同一内容被重复召回，占满 topK 名额。
            while (offset < cleaned.length()) {
                int end = Math.min(offset + MAX_CHUNK_LENGTH, cleaned.length());
                if (end < cleaned.length() && current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                current.append(cleaned, offset, end);
                offset = end;
                if (current.length() >= MAX_CHUNK_LENGTH) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
            }
            current.append('\n');
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        // 没有任何分块说明正文只有空白：直接拒绝，避免落库一篇 publish 永远过不了、检索也命中不到的文章。
        if (chunks.isEmpty()) {
            throw new BusinessException("知识文章正文不能为空");
        }
        return chunks;
    }
}
