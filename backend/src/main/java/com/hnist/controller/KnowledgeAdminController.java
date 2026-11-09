package com.hnist.controller;

import com.hnist.common.constant.AccountRole;

import com.hnist.annotation.CurrentUser;
import com.hnist.annotation.RequireRole;
import com.hnist.common.result.Result;
import com.hnist.vo.KnowledgeArticleDetailResponse;
import com.hnist.dto.SaveKnowledgeArticleRequest;
import com.hnist.entity.KnowledgeArticle;
import com.hnist.common.result.PageResult;
import com.hnist.entity.User;
import com.hnist.service.AuditLogService;
import com.hnist.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 管理端健康知识库控制器。全部写操作（含发布、归档、删除）都会记录审计日志。 */
@RestController
@RequestMapping({"/api/admin/knowledge", "/api/v1/admin/knowledge"})
@RequireRole(AccountRole.ADMIN_CODE)
public class KnowledgeAdminController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final AuditLogService auditLogService;

    public KnowledgeAdminController(KnowledgeBaseService knowledgeBaseService,
                                    AuditLogService auditLogService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public Result<PageResult<KnowledgeArticle>> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.ok(knowledgeBaseService.list(keyword, status, page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<KnowledgeArticleDetailResponse> detail(@PathVariable Long id) {
        return Result.ok(knowledgeBaseService.detail(id));
    }

    @PostMapping
    public Result<KnowledgeArticle> create(
            @Valid @RequestBody SaveKnowledgeArticleRequest request,
            @CurrentUser User operator) {
        KnowledgeArticle article = knowledgeBaseService.save(null, request, operator.getId());
        auditLogService.record(operator.getId(), "KNOWLEDGE_CREATE", "KNOWLEDGE",
                article.getId(), Map.of("status", article.getReviewStatus()));
        return Result.ok("知识文章已保存", article);
    }

    @PutMapping("/{id}")
    public Result<KnowledgeArticle> update(
            @PathVariable Long id,
            @Valid @RequestBody SaveKnowledgeArticleRequest request,
            @CurrentUser User operator) {
        KnowledgeArticle article = knowledgeBaseService.save(id, request, operator.getId());
        auditLogService.record(operator.getId(), "KNOWLEDGE_UPDATE", "KNOWLEDGE",
                id, Map.of("status", article.getReviewStatus()));
        return Result.ok("知识文章已更新", article);
    }

    @PutMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id, @CurrentUser User operator) {
        knowledgeBaseService.publish(id, operator.getId());
        auditLogService.record(operator.getId(), "KNOWLEDGE_PUBLISH",
                "KNOWLEDGE", id, null);
        return Result.ok("知识文章已发布", null);
    }

    @PutMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id, @CurrentUser User operator) {
        knowledgeBaseService.archive(id, operator.getId());
        auditLogService.record(operator.getId(), "KNOWLEDGE_ARCHIVE",
                "KNOWLEDGE", id, null);
        return Result.ok("知识文章已归档", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, @CurrentUser User operator) {
        knowledgeBaseService.delete(id);
        auditLogService.record(operator.getId(), "KNOWLEDGE_DELETE",
                "KNOWLEDGE", id, null);
        return Result.ok("知识文章已删除", null);
    }
}
