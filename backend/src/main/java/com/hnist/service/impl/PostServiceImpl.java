package com.hnist.service.impl;

import com.hnist.domain.ModerationDecision;
import com.hnist.common.constant.AdminPostDisplayStatus;
import com.hnist.common.constant.ModerationStatus;
import com.hnist.service.ContentModerationService;
import com.hnist.common.constant.CommentStatus;
import com.hnist.common.constant.CommunityRules;
import com.hnist.common.constant.PostStatus;
import com.hnist.common.exception.BusinessException;
import com.hnist.mapper.PostMapper;
import com.hnist.mapper.PostCommentMapper;
import com.hnist.mapper.UserMapper;
import com.hnist.dto.CreatePostRequest;
import com.hnist.vo.CommentSubmitResponse;
import com.hnist.vo.LikeToggleResponse;
import com.hnist.vo.MyCommentResponse;
import com.hnist.vo.PostStatsResponse;
import com.hnist.entity.ContentModerationLog;
import com.hnist.common.result.PageResult;
import com.hnist.entity.Post;
import com.hnist.entity.PostComment;
import com.hnist.entity.User;
import com.hnist.event.CommentModerationEvent;
import com.hnist.support.CommunityRateLimitService;
import com.hnist.support.SensitiveWordFilter;
import com.hnist.policy.CommentModerationPolicy;
import com.hnist.support.CommunityPostTransactionService;
import com.hnist.policy.PostModerationPolicy;
import com.hnist.support.PostNotificationFactory;
import com.hnist.event.NotificationEventPublisher;
import com.hnist.service.PostService;
import com.hnist.service.ModerationLogService;
import com.hnist.common.util.IntegerListJsonCodec;
import com.hnist.common.util.PageUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 社区帖子业务实现。 */
@Service
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final PostCommentMapper postCommentMapper;
    private final ContentModerationService contentModerationService;
    private final NotificationEventPublisher notificationPublisher;
    private final ModerationLogService moderationLogService;
    private final ObjectMapper objectMapper;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final CommunityRateLimitService rateLimitService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final CommunityPostTransactionService communityPostTransactionService;

    public PostServiceImpl(PostMapper postMapper, UserMapper userMapper,
                           PostCommentMapper postCommentMapper,
                           ContentModerationService contentModerationService,
                           NotificationEventPublisher notificationPublisher,
                           ModerationLogService moderationLogService,
                           ObjectMapper objectMapper,
                           SensitiveWordFilter sensitiveWordFilter,
                           CommunityRateLimitService rateLimitService,
                           ApplicationEventPublisher applicationEventPublisher,
                           CommunityPostTransactionService communityPostTransactionService) {
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.postCommentMapper = postCommentMapper;
        this.contentModerationService = contentModerationService;
        this.notificationPublisher = notificationPublisher;
        this.moderationLogService = moderationLogService;
        this.objectMapper = objectMapper;
        this.sensitiveWordFilter = sensitiveWordFilter;
        this.rateLimitService = rateLimitService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.communityPostTransactionService = communityPostTransactionService;
    }

    @Override
    public PageResult<Post> getPosts(String category, String keyword, Integer userId,
                                     int page, int pageSize) {
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(
                pageSize, CommunityRules.COMMENT_PAGE_SIZE);
        if (userId != null) {
            // 他人主页只返回审核通过的帖子：status 固定传 normal；作者自己的待审、被拒内容走 getMyPosts，两条路径不能复用。
            List<Post> posts = postMapper.selectByUserId(userId, PostStatus.NORMAL.getCode(),
                    (safePage - 1) * safePageSize, safePageSize);
            int total = postMapper.countByUserId(userId, PostStatus.NORMAL.getCode());
            return new PageResult<>(posts, total, safePage, safePageSize);
        }
        // 公开列表与总数共用 status=normal、deleted_at、分类与关键词同一组过滤，否则分页总数会和列表内容错位。
        List<Post> posts = postMapper.selectByCategory(category, keyword,
                PostStatus.NORMAL.getCode(),
                (safePage - 1) * safePageSize, safePageSize);
        int total = postMapper.countByCategory(
                category, keyword, PostStatus.NORMAL.getCode());
        return new PageResult<>(posts, total, safePage, safePageSize);
    }

    @Override
    public Post getPostById(Integer id) {
        // 详情查询不过滤 status：公开只看 normal、作者可看自己的 pending/blocked，这条差异由调用方 canView 判定；返回 null 仅代表不存在或已软删。
        return postMapper.selectById(id);
    }

    @Override
    public Post createPost(Integer userId, CreatePostRequest request) {
        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new BusinessException(400, "帖子正文不能为空");
        }
        String title = request.getTitle() == null ? null : request.getTitle().trim();
        // 进审核前先限流（30 秒 1 条，单机内存滑动窗口）：只为挡住刷帖占用审核资源，多实例各算各的，不能当安全边界。
        rateLimitService.checkPost(userId);
        long startedAt = System.currentTimeMillis();
        // 敏感词过滤放在调用 AI 之前：本地 DFA 命中即可定性并省一次模型调用，标题必须一起送检，否则可用合规正文夹带违规标题绕过。
        Optional<SensitiveWordFilter.SensitiveMatch> sensitiveMatch =
                sensitiveWordFilter.find((title == null ? "" : title) + content);
        ModerationDecision moderation = resolvePostDecision(
                userId, title, content, request.getImage(), sensitiveMatch.orElse(null));
        Post post = new Post();
        post.setUserId(userId);
        post.setCategory(request.getCategory());
        post.setTitle(title);
        post.setContent(content);
        post.setImage(request.getImage());
        post.setLikes(0);
        post.setComments(0);
        // 只有 ALLOW 直接落 normal 对外可见；REVIEW 与 BLOCK 一律先落 pending 等人工复核，自动审核不会直接判定拒绝。
        post.setStatus(PostModerationPolicy.initialStatus(moderation));
        // 正文与审核日志必须同事务写入：日志的 target_id 依赖 insert 生成的自增主键，所以先落库再记日志，任一步失败整体回滚。
        communityPostTransactionService.saveWithModerationLog(post, moderation,
                sensitiveMatch.isPresent() ? "PREFILTER_AI" : "AI",
                sensitiveRuleJson(sensitiveMatch.orElse(null)),
                excerpt(content),
                System.currentTimeMillis() - startedAt);
        // 通知在落库之后单独发：内容已持久化，通知失败不会回滚帖子；ALLOW 不发提醒，避免与“发布成功”重复打扰。
        publishPostModerationResult(post, moderation.status());
        return post;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Integer postId, Integer userId) {
        Post post = requirePost(postId);
        // 归属校验只认作者本人：非作者直接 403；管理员删帖走 adminDeletePost，不复用这条校验，权限来源在 Controller 的 @RequireRole。
        if (!userId.equals(post.getUserId())) {
            throw new BusinessException(403, "无权删除该帖子");
        }
        postMapper.deleteById(postId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LikeToggleResponse toggleLike(Integer postId, Integer userId) {
        // liked_posts 是 users 表里的 JSON 数组，读-改-写必须串行：先按用户行加锁再解析，否则并发点赞会互相覆盖丢记录。
        User user = userMapper.selectByIdForUpdate(userId);
        Post post = requireNormalPost(postId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        List<Integer> likedPostIds = IntegerListJsonCodec.read(
                objectMapper, user.getLikedPosts());
        boolean liked = likedPostIds.contains(postId);
        if (liked) {
            likedPostIds.remove(postId);
        } else {
            likedPostIds.add(postId);
        }
        // 点赞态与计数分两处写：liked_posts 只表达“这个人点过”，posts.likes 由 SQL 原子加减，前端传值会覆盖掉别人的并发点赞。
        userMapper.updateLikedPosts(userId, IntegerListJsonCodec.write(
                objectMapper, likedPostIds, "点赞数据序列化失败"));
        postMapper.updateLikesCount(postId, liked ? -1 : 1);
        // 只有“本次首次点赞且不是自己的帖子”才通知作者，取消点赞和自赞都是无意义打扰。
        if (!liked && !post.getUserId().equals(userId)) {
            notificationPublisher.publish(
                    PostNotificationFactory.like(post, user, userId));
        }
        // 返回值里的计数是在本次读到的 posts.likes 快照上加减 delta，只用于即时展示；权威值以 updateLikesCount 执行后的行为准。
        return new LikeToggleResponse(!liked, post.getLikes() + (liked ? -1 : 1));
    }

    @Override
    public PageResult<Post> getMyPosts(Integer userId, int page, int pageSize) {
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(
                pageSize, CommunityRules.COMMENT_PAGE_SIZE);
        // 作者视角不筛状态（status 传 null）：自己的待审、被拒帖子都要能查到处理进度，是否公开由前端按 displayStatus 渲染。
        List<Post> posts = postMapper.selectByUserId(userId, null, (safePage - 1) * safePageSize, safePageSize);
        int total = postMapper.countByUserId(userId, null);
        // displayStatus 由 status 加最新审核结论换算：只给 blocked 的话，作者分不清是“审核拒绝”还是“已下架”。
        posts.forEach(post -> post.setDisplayStatus(AdminPostDisplayStatus.resolve(
                post.getStatus(), post.getLatestReviewStatus()).getCode()));
        return new PageResult<>(posts, total, safePage, safePageSize);
    }

    @Override
    public PageResult<Post> getLikedPosts(Integer userId, int page, int pageSize) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        List<Integer> likedPostIds = IntegerListJsonCodec.read(
                objectMapper, user.getLikedPosts());
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(
                pageSize, CommunityRules.COMMENT_PAGE_SIZE);
        if (likedPostIds.isEmpty()) {
            // 没有点赞记录时直接返回空页：IN () 在 MySQL 里不是合法条件，必须提前短路。
            return new PageResult<>(new ArrayList<>(), 0, safePage, safePageSize);
        }
        // 点赞列表只回仍然公开的帖子：selectPublicByIds 内固定 status='normal'，被下架或软删的帖子自动消失，总数用同一条件。
        List<Post> posts = postMapper.selectPublicByIds(
                likedPostIds, (safePage - 1) * safePageSize, safePageSize);
        int total = postMapper.countPublicByIds(likedPostIds);
        return new PageResult<>(posts, total, safePage, safePageSize);
    }

    @Override
    public PageResult<PostComment> getComments(Integer postId, int page, int pageSize) {
        // 这里只校验帖子存在（作者要看自己待审帖的评论区），具体评论由 selectByPostId 按 status='normal' 过滤，待审和被拒评论不外泄。
        requirePost(postId);
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(
                pageSize, CommunityRules.COMMENT_PAGE_SIZE);
        List<PostComment> comments = postCommentMapper.selectByPostId(postId,
                (safePage - 1) * safePageSize, safePageSize);
        int total = postCommentMapper.countByPostId(postId);
        return new PageResult<>(comments, total, safePage, safePageSize);
    }

    @Override
    public PageResult<PostComment> getCommentsForAdmin(Integer postId, int page, int pageSize) {
        // 管理端评论列表不筛状态：人工复核要同时看到 pending 与被拒内容，所以用 selectByPostIdForAdmin 这条不过滤的查询。
        requirePost(postId);
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(pageSize);
        List<PostComment> comments = postCommentMapper.selectByPostIdForAdmin(postId,
                (safePage - 1) * safePageSize, safePageSize);
        int total = postCommentMapper.countByPostIdForAdmin(postId);
        return new PageResult<>(comments, total, safePage, safePageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentSubmitResponse addComment(Integer postId, Integer userId, String content,
                                            Long parentId) {
        // 评论同样先进限流（1 分钟 5 条），与发帖共用单机滑动窗口，先挡刷屏再让内容进审核队列。
        rateLimitService.checkComment(userId);
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isEmpty()) {
            throw new BusinessException("评论内容不能为空");
        }
        // 长度上限取自 CommunityRules.COMMENT_MAX_LENGTH，与前端校验同源：超长正文会拖累列表渲染，也会整段塞进站内信。
        if (normalizedContent.length() > CommunityRules.COMMENT_MAX_LENGTH) {
            throw new BusinessException(
                    "评论不能超过" + CommunityRules.COMMENT_MAX_LENGTH + "个字符");
        }
        Post post = requireNormalPost(postId);
        PostComment parent = null;
        if (parentId != null) {
            // 回复只能挂在本帖下且父评论必须是 normal：允许回复待审评论会让回复区变成未公开内容的旁路；rootId 归一到一级评论，楼层固定两层。
            parent = postCommentMapper.selectById(parentId);
            if (parent == null || !postId.equals(parent.getPostId())
                    || !CommentStatus.NORMAL.getCode().equals(parent.getStatus())) {
                throw new BusinessException("回复的评论不存在");
            }
        }
        PostComment comment = new PostComment();
        comment.setPostId(postId);
        comment.setSenderId(userId);
        comment.setParentId(parentId);
        comment.setRootId(parent == null ? null : (parent.getRootId() == null ? parent.getId() : parent.getRootId()));
        comment.setContent(normalizedContent);
        // 评论一律先以 pending 落库再异步审核：内容不会丢，pending 期间不计入 posts.comments 也不进公开列表，审核结果回写状态后才公开。
        comment.setStatus(CommentStatus.PENDING.getCode());
        postCommentMapper.insert(comment);
        // 事件由 @TransactionalEventListener(AFTER_COMMIT) + @Async 消费：提交后审核线程才能读到这条评论，事务回滚则根本不投递。
        applicationEventPublisher.publishEvent(new CommentModerationEvent(comment.getId()));
        // 固定返回 auditing：提交时审核还没发生，不能让前端以为评论已经通过。
        return new CommentSubmitResponse(
                comment.getId(), "auditing", "评论已发送，正在审核");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Integer postId, Long commentId, Integer userId) {
        requirePost(postId);
        PostComment comment = postCommentMapper.selectById(commentId);
        if (comment == null || !postId.equals(comment.getPostId())) {
            throw new BusinessException(404, "评论不存在");
        }
        // 归属校验只认评论发送者：帖子作者也不能删别人的评论，越权统一 403。
        if (!userId.equals(comment.getSenderId())) {
            throw new BusinessException(403, "只能删除自己的评论");
        }
        postCommentMapper.deleteById(commentId);
        // 只有已公开（normal）的评论才回减计数：pending 从未计入 posts.comments，盲目 -1 会把计数减穿。
        if (CommentStatus.NORMAL.getCode().equals(comment.getStatus())) {
            postMapper.updateCommentsCount(postId, -1);
        }
    }

    @Override
    public PageResult<Post> getAdminPosts(String keyword, String status, String category,
                                          int page, int pageSize) {
        // 管理端筛的是展示状态，不是 posts.status：rejected 与 offline 都落在 blocked，光凭状态列分不开，必须再把 latest_review_status 下推成条件。
        String normalizedStatus = normalizeAdminStatus(status);
        String blockedType = resolveBlockedType(status);
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(pageSize);
        List<Post> posts = postMapper.selectAllForAdmin(keyword, normalizedStatus,
                blockedType, category,
                (safePage - 1) * safePageSize, safePageSize);
        posts.forEach(this::applyAdminDisplayStatus);
        int total = postMapper.countAllForAdmin(
                keyword, normalizedStatus, blockedType, category);
        return new PageResult<>(posts, total, safePage, safePageSize);
    }

    @Override
    public Post getAdminPostById(Integer id) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            return null;
        }
        // 详情必须带最新审核日志：blocked 有两种成因（审核拒绝 / 管理员下架），缺了 reviewStatus 前端和审核页都分不清。
        ContentModerationLog latest = moderationLogService.findLatest(
                "POST", id.longValue());
        post.setLatestReviewStatus(latest == null ? null : latest.getReviewStatus());
        applyAdminDisplayStatus(post);
        return post;
    }

    @Override
    public PostStatsResponse getPostStats() {
        PostStatsResponse response = new PostStatsResponse();
        // 状态计数是包装类型：先归零，保证某状态无数据时仍下发 0 而不是 null。
        response.setNormal(0);
        response.setPending(0);
        response.setBlocked(0);
        int total = 0;
        // 一次 GROUP BY status 聚合后再分发到各字段，避免每个状态各查一次 count；total 顺带累加，不再单独查总数。
        for (Map<String, Object> row : postMapper.statsByStatus()) {
            String status = (String) row.get("status");
            int count = ((Number) row.get("cnt")).intValue();
            total += count;
            if (PostStatus.NORMAL.getCode().equals(status)) {
                response.setNormal(count);
            } else if (PostStatus.PENDING.getCode().equals(status)) {
                response.setPending(count);
            } else if (PostStatus.BLOCKED.getCode().equals(status)) {
                response.setBlocked(count);
            }
        }
        response.setTotal(total);
        // 今日发帖用数据库的 DATE(created_at) = CURDATE() 判定，跨时区部署时以数据库时区为准，不用应用服务器本地日期。
        response.setTodayCount(postMapper.countToday());
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePostStatus(Integer postId, String status) {
        // 状态白名单与流转规则都以 PostModerationPolicy 为准（pending→normal/blocked、normal→blocked、blocked→normal），
        // 其他组合直接拒绝，否则待审帖可以被改回 pending 或反向流转，脱离审核队列。
        if (!PostModerationPolicy.isSupportedStatus(status)) {
            throw new BusinessException("帖子状态不合法");
        }
        Post post = requirePost(postId);
        String previousStatus = post.getStatus();
        if (!PostModerationPolicy.canTransition(previousStatus, status)) {
            throw new BusinessException("帖子状态流转不合法");
        }
        // 条件更新带上 previousStatus：影响行数为 0 说明状态已被其他审核请求改写，抛错让管理员刷新，避免后写覆盖先写的结论。
        if (postMapper.updateStatusIfCurrent(postId, previousStatus, status) == 0) {
            throw new BusinessException("帖子状态已变化，请刷新后重试");
        }
        // 通知在状态落库之后发：文案按前后状态区分通过/拒绝/下架/恢复；同一 dedupeKey 由唯一索引拦截，重复审核不会堆通知。
        publishPostTransition(post, previousStatus, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminDeletePost(Integer postId) {
        // 管理端删除不校验归属：权限由 Controller 的 @RequireRole(ADMIN) 把关，审计流水也在 Controller 侧记 POST_DELETE。
        requirePost(postId);
        postMapper.deleteById(postId);
    }

    @Override
    public PageResult<PostComment> getPendingComments(int page, int pageSize) {
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(
                pageSize, CommunityRules.COMMENT_PAGE_SIZE);
        // 待审队列用 INNER JOIN posts 并排除已删帖：帖子删掉后评论留在队列会“审无可审”，列表与 countPending 也必须同口径。
        List<PostComment> comments = postCommentMapper.selectPending(
                (safePage - 1) * safePageSize, safePageSize);
        return new PageResult<>(comments, postCommentMapper.countPending(),
                safePage, safePageSize);
    }

    @Override
    public PageResult<PostComment> getAdminComments(String status, int page, int pageSize) {
        // status 先过白名单：空值表示不筛状态，非法值直接 400，不会把任意字符串拼进查询条件。
        String normalizedStatus = normalizeCommentStatus(status);
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(pageSize);
        return new PageResult<>(
                postCommentMapper.selectForAdmin(normalizedStatus,
                        (safePage - 1) * safePageSize, safePageSize),
                postCommentMapper.countForAdmin(normalizedStatus),
                safePage, safePageSize);
    }

    @Override
    public PageResult<MyCommentResponse> getMyComments(Integer userId, int page, int pageSize) {
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(pageSize);
        // 我的评论不筛 status：pending 和被拒的评论也要让作者看到处理进度，是否公开由前端按 status 渲染。
        return new PageResult<>(
                postCommentMapper.selectMyComments(userId,
                        (safePage - 1) * safePageSize, safePageSize),
                postCommentMapper.countMyComments(userId),
                safePage, safePageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewComment(Long commentId, String status, Integer operatorId) {
        // 人工复核只接受终态 normal/blocked：不允许把评论改回 pending，否则它会重新混进待审队列，审核结论也无法收敛。
        if (!CommentModerationPolicy.isSupportedReviewResult(status)) {
            throw new BusinessException("评论审核状态不合法");
        }
        PostComment comment = postCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(404, "评论不存在");
        }
        if (!CommentStatus.PENDING.getCode().equals(comment.getStatus())) {
            throw new BusinessException("该评论已处理");
        }
        // updateStatus 的 SQL 自带 status='pending' 条件，同一条评论只会被裁决一次；返回 0 说明已被别的管理员处理，提示刷新而不是覆盖。
        if (postCommentMapper.updateStatus(commentId, status) == 0) {
            throw new BusinessException("评论审核状态已变化，请刷新后重试");
        }
        // 审核结论落 approved/rejected，SQL 只更新 review_status='pending' 的日志，重复提交不会改写第一次的人工结论。
        moderationLogService.review("COMMENT", commentId, operatorId,
                CommentModerationPolicy.reviewLogStatus(status));
        Post post = requirePost(comment.getPostId());
        // 计数只在通过时 +1，与异步审核（CommentModerationPersistenceService.applyDecision）口径一致；pending 期间从未计数，所以拒绝时不能回减。
        if (CommentStatus.NORMAL.getCode().equals(status)) {
            postMapper.updateCommentsCount(post.getId(), 1);
            // 通过时通知帖子作者有新评论；自评不发，避免自己收到自己。
            if (!post.getUserId().equals(comment.getSenderId())) {
                notificationPublisher.publish(
                        PostNotificationFactory.commentApproved(post, comment));
            }
        } else {
            // 拒绝必须通知评论作者本人：他不知道内容被拦，会以为发送失败而反复提交。
            notificationPublisher.publish(
                    PostNotificationFactory.commentRejected(post, comment, operatorId));
        }
    }

    private ModerationDecision resolvePostDecision(
            Integer userId, String title, String content, String imageUrl,
            SensitiveWordFilter.SensitiveMatch sensitiveMatch) {
        // 命中高风险敏感词时不送 AI、也不直接拒绝：直接转 REVIEW 交人工，既避免误杀，也省一次模型调用。
        if (sensitiveMatch != null && sensitiveMatch.severity() == ModerationStatus.BLOCK) {
            return new ModerationDecision(
                    ModerationStatus.REVIEW,
                    "内容命中高风险规则，需人工复核：" + sensitiveMatch.category(),
                    "high", "[\"" + sensitiveMatch.category() + "\"]",
                    "dfa", "sensitive-word-v1", imageUrl, null,
                    sensitiveRuleJson(sensitiveMatch), 1.0D);
        }
        ModerationDecision decision = contentModerationService.moderatePost(
                userId, title, content, imageUrl);
        // 两边结论取更严格的一侧：AI 判 ALLOW 但本地命中 REVIEW 级敏感词时，升级为人工复核，本地规则优先于模型。
        if (sensitiveMatch == null || sensitiveMatch.severity() != ModerationStatus.REVIEW
                || decision.status() != ModerationStatus.ALLOW) {
            return decision;
        }
        return new ModerationDecision(
                ModerationStatus.REVIEW,
                "内容命中需复核规则：" + sensitiveMatch.category(),
                "medium", "[\"" + sensitiveMatch.category() + "\"]",
                decision.modelName(), decision.promptVersion(), decision.imageUrl(),
                decision.imageHash(), sensitiveRuleJson(sensitiveMatch),
                decision.confidence());
    }

    private void publishPostModerationResult(Post post, ModerationStatus status) {
        // 只有转人工才提醒作者“正在复核”：ALLOW 已经等同发布成功，再发一条只会和发布成功提示重复。
        if (status == ModerationStatus.ALLOW) {
            return;
        }
        notificationPublisher.publish(
                PostNotificationFactory.moderationPending(post, status));
    }

    private void publishPostTransition(Post post, String previousStatus, String currentStatus) {
        // blocked→normal 有两种语义（复核通过 / 恢复上架），要靠最新审核日志的 reviewStatus 区分，文案由 PostNotificationFactory.postTransition 决定。
        ContentModerationLog latest = moderationLogService.findLatest(
                "POST", post.getId().longValue());
        notificationPublisher.publish(
                PostNotificationFactory.postTransition(
                        post, previousStatus, currentStatus, latest));
    }

    private String sensitiveRuleJson(SensitiveWordFilter.SensitiveMatch match) {
        if (match == null) {
            return null;
        }
        return "{\"word\":\"" + match.word()
                + "\",\"category\":\"" + match.category() + "\"}";
    }

    private String excerpt(String content) {
        if (content == null) {
            return null;
        }
        // 审核日志只存前 POST_EXCERPT_MAX_LENGTH 个字符：够人工回溯判定，又不会把全文再复制一份进日志表。
        return content.length() <= CommunityRules.POST_EXCERPT_MAX_LENGTH
                ? content : content.substring(0, CommunityRules.POST_EXCERPT_MAX_LENGTH);
    }

    private String normalizeCommentStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toLowerCase();
        if (!CommentModerationPolicy.isSupportedStatus(normalized)) {
            throw new BusinessException("评论状态不合法");
        }
        return normalized;
    }

    private String normalizeAdminStatus(String status) {
        if (AdminPostDisplayStatus.PUBLISHED.getCode().equals(status)) {
            return PostStatus.NORMAL.getCode();
        }
        if (AdminPostDisplayStatus.REVIEW_REJECTED.getCode().equals(status)
                || AdminPostDisplayStatus.OFFLINE.getCode().equals(status)) {
            return PostStatus.BLOCKED.getCode();
        }
        return status;
    }

    private String resolveBlockedType(String status) {
        // 返回 null 表示不加二次过滤；只有 rejected/offline 需要按最新审核结论再切一刀，其余展示状态只靠 posts.status 就够。
        return AdminPostDisplayStatus.REVIEW_REJECTED.getCode().equals(status)
                || AdminPostDisplayStatus.OFFLINE.getCode().equals(status)
                ? status : null;
    }

    private void applyAdminDisplayStatus(Post post) {
        post.setDisplayStatus(AdminPostDisplayStatus.resolve(
                post.getStatus(), post.getLatestReviewStatus()).getCode());
    }

    private Post requirePost(Integer postId) {
        // 不存在与已软删统一按 404「帖子不存在」返回：调用方只需处理一种失败语义，不必各自判空。
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(404, "帖子不存在");
        }
        return post;
    }

    private Post requireNormalPost(Integer postId) {
        Post post = requirePost(postId);
        // 互动和评论区只认 normal 帖子：pending/blocked 一律按 404 处理，不暴露“存在但未通过”；点赞、发评论、读评论都复用这条校验。
        if (!PostStatus.NORMAL.getCode().equals(post.getStatus())) {
            throw new BusinessException(404, "帖子不存在或尚未公开");
        }
        return post;
    }

}
