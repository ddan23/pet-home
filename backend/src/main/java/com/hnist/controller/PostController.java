package com.hnist.controller;

import com.hnist.annotation.CurrentUser;
import com.hnist.annotation.RequireRole;
import com.hnist.common.constant.AccountRole;
import com.hnist.common.constant.PostStatus;
import com.hnist.common.result.Result;
import com.hnist.dto.AddCommentRequest;
import com.hnist.dto.CreatePostRequest;
import com.hnist.vo.CommentSubmitResponse;
import com.hnist.vo.LikedIdsResponse;
import com.hnist.vo.LikeToggleResponse;
import com.hnist.vo.MyCommentResponse;
import com.hnist.vo.ModerationUnreadSummaryResponse;
import com.hnist.vo.PostStatsResponse;
import com.hnist.dto.UpdateStatusRequest;
import com.hnist.common.result.PageResult;
import com.hnist.entity.Post;
import com.hnist.entity.PostComment;
import com.hnist.entity.User;
import com.hnist.entity.ContentModerationLog;
import com.hnist.service.ModerationDashboardService;
import com.hnist.service.PostService;
import com.hnist.service.PostReviewService;
import com.hnist.service.AuditLogService;
import com.hnist.service.ModerationLogService;
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

/** 社区帖子控制器。 */
@RestController
@RequestMapping({"/api", "/api/v1"})
public class PostController {

    private final PostService postService;
    private final PostReviewService postReviewService;
    private final AuditLogService auditLogService;
    private final ModerationLogService moderationLogService;
    private final ModerationDashboardService moderationDashboardService;

    public PostController(PostService postService, PostReviewService postReviewService,
                          AuditLogService auditLogService,
                          ModerationLogService moderationLogService,
                          ModerationDashboardService moderationDashboardService) {
        this.postService = postService;
        this.postReviewService = postReviewService;
        this.auditLogService = auditLogService;
        this.moderationLogService = moderationLogService;
        this.moderationDashboardService = moderationDashboardService;
    }

    @GetMapping("/posts")
    public Result<PageResult<Post>> listPosts(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "userId", required = false) Integer userId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "12") int pageSize) {
        if ("全部".equals(category)) {
            category = null;
        }
        return Result.ok(postService.getPosts(category, keyword, userId, page, pageSize));
    }

    @GetMapping("/posts/{id}")
    public Result<Post> getPost(@PathVariable Integer id,
                                @CurrentUser(required = false) User currentUser) {
        Post post = postService.getPostById(id);
        if (post == null || !canView(post, currentUser)) {
            return Result.fail(404, "帖子不存在");
        }
        return Result.ok(post);
    }

    @GetMapping("/posts/{id}/comments")
    public Result<PageResult<PostComment>> listComments(
            @PathVariable Integer id,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
            @CurrentUser(required = false) User currentUser) {
        Post post = postService.getPostById(id);
        if (post == null || !canView(post, currentUser)) {
            return Result.fail(404, "帖子不存在");
        }
        return Result.ok(postService.getComments(id, page, pageSize));
    }

    @GetMapping("/user/likedIds")
    public Result<LikedIdsResponse> getLikedIds(@CurrentUser(required = false) User user) {
        String likedPostIds = user == null || user.getLikedPosts() == null ? "[]" : user.getLikedPosts();
        return Result.ok(new LikedIdsResponse(likedPostIds));
    }

    /** 发布结果由自动审核状态决定：pending 进入人工复核、blocked 暂不公开，两者都不算失败。 */
    @PostMapping("/posts")
    @RequireRole(AccountRole.USER_CODE)
    public Result<Post> createPost(@Valid @RequestBody CreatePostRequest request,
                                   @CurrentUser User user) {
        Post post = postService.createPost(user.getId(), request);
        if (PostStatus.PENDING.getCode().equals(post.getStatus())) {
            return Result.ok("发布成功，内容正在人工复核", post);
        }
        if (PostStatus.BLOCKED.getCode().equals(post.getStatus())) {
            return Result.ok("内容未通过自动审核，暂不公开", post);
        }
        return Result.ok("发布成功", post);
    }

    @PostMapping("/posts/{id}/comments")
    @RequireRole(AccountRole.USER_CODE)
    public Result<CommentSubmitResponse> addComment(
            @PathVariable Integer id,
            @Valid @RequestBody AddCommentRequest request,
            @CurrentUser User user) {
        CommentSubmitResponse response = postService.addComment(
                id, user.getId(), request.getContent(), request.getParentId());
        return Result.ok(response.getMessage(), response);
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    @RequireRole(AccountRole.USER_CODE)
    public Result<Void> deleteComment(@PathVariable Integer postId, @PathVariable Long commentId,
                                      @CurrentUser User user) {
        postService.deleteComment(postId, commentId, user.getId());
        return Result.ok("删除成功", null);
    }

    @PostMapping("/posts/{id}/like")
    @RequireRole(AccountRole.USER_CODE)
    public Result<LikeToggleResponse> toggleLike(@PathVariable Integer id, @CurrentUser User user) {
        return Result.ok(postService.toggleLike(id, user.getId()));
    }

    @DeleteMapping("/posts/{id}")
    @RequireRole(AccountRole.USER_CODE)
    public Result<Void> deletePost(@PathVariable Integer id, @CurrentUser User user) {
        postService.deletePost(id, user.getId());
        return Result.ok("删除成功", null);
    }

    @GetMapping("/user/posts")
    @RequireRole(AccountRole.USER_CODE)
    public Result<PageResult<Post>> listMyPosts(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "12") int pageSize,
            @CurrentUser User user) {
        return Result.ok(postService.getMyPosts(user.getId(), page, pageSize));
    }

    @GetMapping("/user/posts/liked")
    @RequireRole(AccountRole.USER_CODE)
    public Result<PageResult<Post>> listLikedPosts(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "12") int pageSize,
            @CurrentUser User user) {
        return Result.ok(postService.getLikedPosts(user.getId(), page, pageSize));
    }

    @GetMapping("/admin/posts")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<PageResult<Post>> listAdminPosts(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.ok(postService.getAdminPosts(keyword, status, category, page, pageSize));
    }

    @GetMapping("/admin/posts/{id}")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<Post> getAdminPost(@PathVariable Integer id) {
        Post post = postService.getAdminPostById(id);
        if (post == null) {
            return Result.fail(404, "帖子不存在");
        }
        return Result.ok(post);
    }

    @GetMapping("/admin/posts/stats")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<PostStatsResponse> getPostStats() {
        return Result.ok(postService.getPostStats());
    }

    @GetMapping("/admin/moderation/unread-summary")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<ModerationUnreadSummaryResponse> getModerationUnreadSummary() {
        return Result.ok(moderationDashboardService.getUnreadSummary());
    }

    @GetMapping("/admin/posts/{id}/moderation")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<ContentModerationLog> getPostModeration(@PathVariable Integer id) {
        return Result.ok(moderationLogService.findLatest("POST", id.longValue()));
    }

    @GetMapping("/admin/posts/{id}/comments")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<PageResult<PostComment>> listAdminComments(
            @PathVariable Integer id,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.ok(postService.getCommentsForAdmin(id, page, pageSize));
    }

    @GetMapping("/admin/comments/pending")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<PageResult<PostComment>> listPendingComments(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.ok(postService.getPendingComments(page, pageSize));
    }

    @GetMapping("/admin/comments/{id}/moderation")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<ContentModerationLog> getCommentModeration(@PathVariable Long id) {
        return Result.ok(moderationLogService.findLatest("COMMENT", id));
    }

    @GetMapping("/admin/comments")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<PageResult<PostComment>> listAdminComments(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.ok(postService.getAdminComments(status, page, pageSize));
    }

    @GetMapping({"/user/my-comments", "/user/comments"})
    @RequireRole(AccountRole.USER_CODE)
    public Result<PageResult<MyCommentResponse>> listMyComments(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "8") int pageSize,
            @CurrentUser User user) {
        return Result.ok(postService.getMyComments(user.getId(), page, pageSize));
    }

    @PutMapping("/admin/comments/{id}/status")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<Void> reviewComment(@PathVariable Long id,
                                      @Valid @RequestBody UpdateStatusRequest request,
                                      @CurrentUser User operator) {
        postService.reviewComment(id, request.getStatus(), operator.getId());
        auditLogService.record(operator.getId(), "COMMENT_REVIEW", "COMMENT", id,
                Map.of("status", request.getStatus()));
        return Result.ok("评论审核完成", null);
    }

    @PutMapping("/admin/posts/{id}/status")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<Void> updatePostStatus(@PathVariable Integer id,
                                         @Valid @RequestBody UpdateStatusRequest request,
                                         @CurrentUser User operator) {
        postReviewService.reviewPost(id, request.getStatus(), operator.getId());
        return Result.ok("更新成功", null);
    }

    @DeleteMapping("/admin/posts/{id}")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<Void> deleteAdminPost(@PathVariable Integer id, @CurrentUser User operator) {
        postService.adminDeletePost(id);
        auditLogService.record(operator.getId(), "POST_DELETE", "POST", id.longValue(), null);
        return Result.ok("删除成功", null);
    }

    /** 非 NORMAL 状态帖子仅作者本人可见，对其他人（含匿名）一律 404，不暴露审核中内容的存在性。 */
    private boolean canView(Post post, User currentUser) {
        return PostStatus.NORMAL.getCode().equals(post.getStatus())
                || (currentUser != null
                && AccountRole.USER.getCode().equals(currentUser.getRole())
                && currentUser.getId().equals(post.getUserId()));
    }

}
