package com.hnist.service;

import com.hnist.dto.CreatePostRequest;
import com.hnist.vo.CommentSubmitResponse;
import com.hnist.vo.LikeToggleResponse;
import com.hnist.vo.MyCommentResponse;
import com.hnist.vo.PostStatsResponse;
import com.hnist.common.result.PageResult;
import com.hnist.entity.Post;
import com.hnist.entity.PostComment;

/** 社区帖子业务服务。 */
public interface PostService {

    /** 传 userId 时按作者主页口径只返回 normal 帖子；不传则按分类、关键词给公开列表，分页参数在实现内归一。 */
    PageResult<Post> getPosts(String category, String keyword, Integer userId, int page, int pageSize);

    /** 不过滤审核状态，公开性由调用方判定；返回 null 只代表不存在或已软删。 */
    Post getPostById(Integer id);

    /** 审核判定与落库在同一事务完成，返回对象的 status 就是结论：normal 已公开，pending 等待人工复核。 */
    Post createPost(Integer userId, CreatePostRequest request);

    /** 仅作者本人可调用，越权抛 403；管理员删除走 adminDeletePost。 */
    void deletePost(Integer postId, Integer userId);

    /** 切换当前用户对该帖的点赞态，计数由 SQL 原子加减，实现不接受外部传入的计数。 */
    LikeToggleResponse toggleLike(Integer postId, Integer userId);

    /** 含本人的 pending/blocked 帖子，displayStatus 已按最新审核结论换算。 */
    PageResult<Post> getMyPosts(Integer userId, int page, int pageSize);

    /** 只返回仍为 normal 的帖子，已下架或已删除的点赞记录不会出现在列表里。 */
    PageResult<Post> getLikedPosts(Integer userId, int page, int pageSize);

    /** 只返回 normal 评论；这里不校验帖子状态，作者预览自己待审帖的评论区会用到这一点。 */
    PageResult<PostComment> getComments(Integer postId, int page, int pageSize);

    /** 管理端视角，包含 pending 与被拒评论，供人工复核。 */
    PageResult<PostComment> getCommentsForAdmin(Integer postId, int page, int pageSize);

    /** 评论先以 pending 落库再异步审核，返回 auditing，调用方不能假设已公开。 */
    CommentSubmitResponse addComment(Integer postId, Integer userId, String content, Long parentId);

    /** 仅评论发送者可删，帖子作者无权代删；只有 normal 评论会回减 posts.comments。 */
    void deleteComment(Integer postId, Long commentId, Integer userId);

    /** status 是管理端展示状态（published/pending/rejected/offline），实现内再映射成 posts.status 与审核结论过滤。 */
    PageResult<Post> getAdminPosts(String keyword, String status, String category,
                                   int page, int pageSize);

    /** 管理端详情，附最新审核结论以区分审核拒绝与管理员下架；不存在返回 null。 */
    Post getAdminPostById(Integer id);

    PostStatsResponse getPostStats();

    /** 流转受 PostModerationPolicy 白名单约束；状态已被并发改写时抛异常，要求刷新后重试。 */
    void updatePostStatus(Integer postId, String status);

    /** 不做归属校验，管理员身份由 Controller 的角色注解保证。 */
    void adminDeletePost(Integer postId);

    /** 只含待审评论，并排除所属帖子已删除的记录。 */
    PageResult<PostComment> getPendingComments(int page, int pageSize);

    PageResult<PostComment> getAdminComments(String status, int page, int pageSize);

    PageResult<MyCommentResponse> getMyComments(Integer userId, int page, int pageSize);

    /** 只接受 pending→normal/blocked，重复审核抛异常；posts.comments 仅在通过时 +1。 */
    void reviewComment(Long commentId, String status, Integer operatorId);
}
