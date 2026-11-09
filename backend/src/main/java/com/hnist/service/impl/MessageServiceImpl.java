package com.hnist.service.impl;

import com.hnist.common.constant.AccountStatus;
import com.hnist.common.constant.AccountRole;
import com.hnist.common.constant.ConversationCategory;
import com.hnist.common.exception.BusinessException;
import com.hnist.vo.ConversationMessagePage;
import com.hnist.vo.ConversationUnreadCount;
import com.hnist.dto.CreateConversationRequest;
import com.hnist.dto.SendConversationMessageRequest;
import com.hnist.dto.SendMessageRequest;
import com.hnist.vo.SendMessageResponse;
import com.hnist.mapper.MessageMapper;
import com.hnist.mapper.UserMapper;
import com.hnist.entity.Conversation;
import com.hnist.entity.Message;
import com.hnist.entity.User;
import com.hnist.support.ConversationRateLimitService;
import com.hnist.sse.ConversationSseService;
import com.hnist.service.MessageService;
import com.hnist.common.result.PageResult;
import com.hnist.common.util.PageUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 会话开通、消息收发、已读游标与未读计数的实现。 */
@Service
public class MessageServiceImpl implements MessageService {

    // 单页消息条数硬上限：limit 由前端传入，规范化时夹在 [1, 100]，避免一次拉取整段历史压垮连接与前端渲染。
    private static final int MAX_MESSAGE_PAGE_SIZE = 100;
    // 会话摘要长度：这段文本会写进 conversations.last_message，超长正文必须先截断再入库。
    private static final int MESSAGE_PREVIEW_LENGTH = 100;

    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final ConversationSseService conversationSseService;
    private final ConversationRateLimitService rateLimitService;

    public MessageServiceImpl(MessageMapper messageMapper, UserMapper userMapper,
                              ConversationSseService conversationSseService,
                              ConversationRateLimitService rateLimitService) {
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
        this.conversationSseService = conversationSseService;
        this.rateLimitService = rateLimitService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SendMessageResponse sendMessage(Integer senderId, SendMessageRequest request) {
        // 旧版发送接口可能只带 messageId：接收人取被回复消息的 senderId，回复方向由原消息决定而不是前端自报。
        Integer receiverId = request.getReceiverId();
        if (receiverId == null && request.getMessageId() != null) {
            Message originalMessage = messageMapper.selectById(request.getMessageId());
            receiverId = originalMessage == null ? null : originalMessage.getSenderId();
        }
        if (receiverId == null) {
            throw new BusinessException("接收人不能为空");
        }

        // type 沿用旧版业务别名（private_letter/customer_service/consult），fromAlias 同时接受分类 code；
        // 未知值在此拒收，避免写库时撞 messages 表的 chk_message_scene。
        String rawType = request.getType() == null ? "community" : request.getType();
        ConversationCategory category = ConversationCategory.fromAlias(rawType);
        if (category == null) {
            throw new BusinessException("消息类型不合法");
        }

        CreateConversationRequest createRequest = new CreateConversationRequest();
        createRequest.setTargetUserId(receiverId);
        createRequest.setCategory(category.getCode());
        createRequest.setType(category.getMessageType());
        Conversation conversation = openConversation(senderId, createRequest);

        SendConversationMessageRequest messageRequest = new SendConversationMessageRequest();
        messageRequest.setContent(request.getContent());
        // 旧接口没有客户端幂等键：这里补一个随机值，让去重查询与 uk_message_client 这条链路照常生效。
        messageRequest.setClientMessageId(UUID.randomUUID().toString());
        messageRequest.setContentType("text");
        Message message = sendConversationMessage(conversation.getId(), senderId, messageRequest);
        return new SendMessageResponse(conversation.getId(), message.getId(),
                conversation.getCategory(), conversation.getType());
    }

    @Override
    public PageResult<Conversation> listConversationSummaries(
            Integer userId, List<String> categories, int page, int pageSize) {
        validateCategories(categories);
        return conversationPage(userId, categories, page, pageSize);
    }

    @Override
    public PageResult<Conversation> listMerchantConversationSummaries(
            Integer userId, List<String> categories, int page, int pageSize) {
        // 商家侧默认白名单只有商城客服与服务咨询：商家与用户的社区私信不应出现在商家后台。
        List<String> merchantCategories = List.of(
                ConversationCategory.MALL.getCode(), ConversationCategory.SERVICE.getCode());
        if (categories == null || categories.isEmpty()) {
            return conversationPage(userId, merchantCategories, page, pageSize);
        }
        validateCategories(categories);
        // 显式传入其它分类直接 403 而不是静默过滤：把越权请求暴露给调用方，避免前端误以为拿到了完整列表。
        if (!merchantCategories.containsAll(categories)) {
            throw new BusinessException(403, "商家只能查询商城和服务会话");
        }
        return conversationPage(userId, categories, page, pageSize);
    }

    @Override
    public Conversation getConversationForUser(Integer conversationId, Integer userId) {
        // 可见性由 conversation_participants 决定：SQL 要求当前 userId 的参与记录存在且 deleted_at 为空，
        // 他人会话与已删除会话都查不到，统一返回 404，不区分“不存在”和“无权访问”。
        Conversation conversation = messageMapper.selectConversationForUser(
                conversationId, userId);
        if (conversation == null) {
            throw new BusinessException(404, "会话不存在或无权访问");
        }
        return conversation;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Conversation openConversation(Integer userId, CreateConversationRequest request) {
        Integer targetUserId = request.getTargetUserId();
        // 不允许与自己建会话：uk_user_target_cat 无法区分方向，自聊会让同一条记录同时代表两个身份。
        if (userId.equals(targetUserId)) {
            throw new BusinessException("不能与自己创建会话");
        }
        User requester = requireChatUser(userId);
        User targetUser = userMapper.selectById(targetUserId);
        if (targetUser == null) {
            throw new BusinessException(404, "会话对象不存在");
        }
        // 对方账号必须存在且为 normal：会话建立后未读会挂在对方身上，禁用账号无法处理也看不到。
        if (!AccountStatus.NORMAL.getCode().equals(targetUser.getStatus())) {
            throw new BusinessException("会话对象账号不可用");
        }
        // 分类与消息类型必须成对：conversations 与 messages 都有 chk_*_scene 约束，这里提前拦成业务异常而不是让数据库报错。
        ConversationCategory category = validateConversationType(
                request.getCategory(), request.getType());
        if (category == ConversationCategory.COMMUNITY) {
            // 社区私信只允许普通用户互发：商家触达用户只能走商城客服或服务咨询入口。
            if (!AccountRole.USER.getCode().equals(requester.getRole())
                    || !AccountRole.USER.getCode().equals(targetUser.getRole())) {
                throw new BusinessException("社区私信仅允许普通用户之间使用");
            }
        } else {
            // 商城客服与服务咨询的方向固定为用户联系商家：商家不能主动开新会话，未读归属因此唯一。
            if (!AccountRole.USER.getCode().equals(requester.getRole())
                    || !AccountRole.BUSINESS.getCode().equals(targetUser.getRole())) {
                throw new BusinessException("商城客服和服务咨询必须由普通用户联系商家");
            }
        }

        Conversation existing = messageMapper.selectConversationByPair(
                userId, targetUserId, category.getCode());
        if (existing == null) {
            // user_id/target_id 按大小归一化：uk_user_target_cat 只认单向顺序，归一化后无论谁发起都落到同一条会话。
            int lowerUserId = Math.min(userId, targetUserId);
            int higherUserId = Math.max(userId, targetUserId);
            Conversation conversation = new Conversation();
            conversation.setCategory(category.getCode());
            conversation.setType(category.getMessageType());
            conversation.setUserId(lowerUserId);
            conversation.setTargetId(higherUserId);
            try {
                messageMapper.insertConversation(conversation);
                existing = conversation;
            } catch (DuplicateKeyException exception) {
                // 并发开通时双方可能同时判定会话不存在：唯一索引只放行一条插入，失败方回查已存在的记录继续后续步骤。
                existing = messageMapper.selectConversationByPair(
                        userId, targetUserId, category.getCode());
            }
        }
        if (existing == null) {
            throw new BusinessException(500, "创建会话失败");
        }
        // 重新开聊要恢复双方参与记录：restoreConversation 只清发起人的 deleted_at，
        // insertParticipant 用 INSERT IGNORE 补齐缺失的一方；
        // 对方的 deleted_at 由下一次 incrementParticipantUnread 里的 deleted_at = NULL 唤醒。
        messageMapper.restoreConversation(existing.getId(), userId);
        messageMapper.insertParticipant(existing.getId(), userId);
        messageMapper.insertParticipant(existing.getId(), targetUserId);
        return getConversationForUser(existing.getId(), userId);
    }

    @Override
    public ConversationMessagePage listConversationMessages(Integer conversationId,
                                                            Integer userId,
                                                            Integer beforeId,
                                                            int limit) {
        getConversationForUser(conversationId, userId);
        // 清除水位下推到 SQL：只取 id > cleared_before_message_id，用户删过会话后旧消息不再返回。
        Integer afterMessageId =
                messageMapper.selectClearedBeforeMessageId(conversationId, userId);
        int safeLimit = Math.min(Math.max(limit, 1), MAX_MESSAGE_PAGE_SIZE);
        // 多取一条用于探测 hasMore：探测行只参与判断，不返回给前端。
        List<Message> descending = messageMapper.selectLatestMessages(
                conversationId, afterMessageId, beforeId, safeLimit + 1);
        boolean hasMore = descending.size() > safeLimit;
        if (hasMore) {
            descending = new ArrayList<>(descending.subList(0, safeLimit));
        } else {
            descending = new ArrayList<>(descending);
        }
        Integer nextBeforeId = descending.isEmpty()
                ? beforeId : descending.get(descending.size() - 1).getId();
        // SQL 按 id 倒序取最近 N 条（游标分页比 offset 稳定），这里翻转为时间正序供前端直接追加渲染。
        Collections.reverse(descending);
        return new ConversationMessagePage(descending, hasMore, nextBeforeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Message sendConversationMessage(Integer conversationId, Integer userId,
                                           SendConversationMessageRequest request) {
        requireChatUser(userId);
        Conversation conversation = getConversationForUser(conversationId, userId);
        // 先锁会话行再查重：同一会话的发送被串行化，client_message_id 的“查不到就插入”不会被并发写穿。
        messageMapper.lockConversationForUpdate(conversationId);
        Message duplicate = messageMapper.selectByClientMessageId(
                conversationId, userId, request.getClientMessageId());
        // 命中幂等键视为重发：原样返回已存消息，不重复写库、不重复累加未读、不重复推送。
        if (duplicate != null) {
            return duplicate;
        }
        // 限流放在去重之后：重发同一 clientMessageId 不消耗配额，只有真正的新消息才计数。
        rateLimitService.checkMessage(userId);

        // 接收人由参与人列表推导：除自己外的唯一参与者就是 receiverId，取不到说明参与记录缺失，宁可报错也不写一条没有归属的消息。
        List<Integer> participantIds = messageMapper.selectParticipantIds(conversationId);
        Integer receiverId = participantIds.stream()
                .filter(id -> !userId.equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException("会话参与者数据不完整"));
        Message message = createMessage(conversation, userId, receiverId, request);
        try {
            messageMapper.insert(message);
        } catch (DuplicateKeyException exception) {
            // uk_message_client 兜住并发重复：冲突后回查同 clientMessageId 的消息返回，客户端不会看到“报错但消息其实已入库”。
            Message savedMessage = messageMapper.selectByClientMessageId(
                    conversationId, userId, request.getClientMessageId());
            if (savedMessage == null) {
                throw exception;
            }
            return savedMessage;
        }

        String preview = buildPreview(message);
        // 未读与最后一条消息由 SQL 原地维护：updateConversationLastMessage 覆盖
        // last_message_id/last_message_at，incrementParticipantUnread 只做 unread_count + 1。
        // 应用层“读出来加一再写回”在并发发送时会丢计数，也会用旧摘要覆盖新摘要。
        messageMapper.updateConversationLastMessage(
                conversationId, message.getId(), preview, message.getCreatedAt());
        messageMapper.incrementParticipantUnread(conversationId, receiverId);
        Map<String, Object> event = Map.of(
                "conversationId", conversationId,
                "senderId", userId,
                "message", message
        );
        // 先落库再推送：SSE 只是通知在线端刷新，推送异常或对方不在线都不影响已提交的消息与未读数。
        pushAfterCommit(participantIds, "conversation-message", event);
        return message;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markConversationRead(Integer conversationId, Integer userId,
                                    Integer lastMessageId) {
        Conversation conversation = getConversationForUser(conversationId, userId);
        Integer readCursor = resolveReadCursor(conversation, lastMessageId);
        // 两级已读都要写：messages.is_read/read_at 是单条已读回执，conversation_participants.unread_count 是会话列表的未读来源，
        // 只更新一边会出现“列表有红点但聊天页全已读”。
        messageMapper.markConversationMessagesRead(conversationId, userId, readCursor);
        // updateParticipantRead 用子查询重算 unread_count，游标在 CASE 里只前进不回退，并发标记已读不会把 last_read_message_id 改小。
        messageMapper.updateParticipantRead(conversationId, userId, readCursor);
        List<Integer> participantIds = messageMapper.selectParticipantIds(conversationId);
        Map<String, Object> event = Map.of(
                "conversationId", conversationId,
                "readerId", userId,
                "lastReadMessageId", readCursor == null ? 0 : readCursor
        );
        // 已读回执只推给对方：自己的未读状态由接口返回值带回，避免多端重复提示。
        pushAfterCommit(participantIds.stream().filter(id -> !userId.equals(id)).toList(),
                "conversation-read", event);
        // 返回跨会话未读总数：前端顶部红点直接用该值，省掉一次聚合请求。
        return messageMapper.sumConversationUnread(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(Integer conversationId, Integer userId) {
        getConversationForUser(conversationId, userId);
        messageMapper.lockConversationForUpdate(conversationId);
        // 加锁后重新读取：hideConversation 要把当前 lastMessageId 写成 cleared_before_message_id，这个水位必须是最新值，否则重新开聊会把已删消息又拉回来。
        Conversation conversation = getConversationForUser(conversationId, userId);
        // 影响行数为 0 说明会话已被并发删除（SQL 带 deleted_at IS NULL）：抛错而不是静默成功，前端才不会误以为删除生效。
        if (messageMapper.hideConversation(
                conversationId, userId, conversation.getLastMessageId()) == 0) {
            throw new BusinessException("会话已删除或状态已变化");
        }
        // 只推给自己：删除是单方视角的软删除，对方会话不受影响。
        pushAfterCommit(List.of(userId), "conversation-deleted",
                Map.of("conversationId", conversationId));
    }

    @Override
    public int getConversationUnreadCount(Integer userId) {
        return messageMapper.sumConversationUnread(userId);
    }

    @Override
    public Map<String, Integer> getConversationUnreadCountsByCategory(Integer userId) {
        Map<String, Integer> unreadCounts = new HashMap<>();
        // 先按枚举把所有分类铺成 0：前端分类 tab 依赖固定键集合，SQL 只返回真正有未读的分类。
        for (ConversationCategory category : ConversationCategory.values()) {
            unreadCounts.put(category.getCode(), 0);
        }
        List<ConversationUnreadCount> queryResults =
                messageMapper.selectConversationUnreadCountsByCategory(userId);
        for (ConversationUnreadCount item : queryResults) {
            if (item.getCategory() != null && item.getUnreadCount() != null) {
                unreadCounts.put(item.getCategory(), item.getUnreadCount());
            }
        }
        return unreadCounts;
    }

    private Message createMessage(Conversation conversation, Integer senderId,
                                  Integer receiverId,
                                  SendConversationMessageRequest request) {
        Message message = new Message();
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        // 分类与类型继承会话而不是取请求参数：messages 表 chk_message_scene 要求两表场景一致，否则插入直接被约束拒绝。
        message.setCategory(conversation.getCategory());
        message.setType(conversation.getType());
        message.setConversationId(conversation.getId());
        message.setTitle("");
        message.setContent(request.getContent().trim());
        message.setContentType(defaultContentType(request.getContentType()));
        message.setRelatedType(request.getRelatedType());
        message.setRelatedId(request.getRelatedId());
        message.setClientMessageId(request.getClientMessageId());
        // 创建时间由应用生成：同一个值随后要写进 conversations.last_message_at 作为会话排序时间，不能依赖数据库默认值造成两个时间不一致。
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    private Integer resolveReadCursor(Conversation conversation, Integer lastMessageId) {
        // 不传游标表示“读到最新”：直接取会话当前的 last_message_id，不额外查消息表。
        if (lastMessageId == null) {
            return conversation.getLastMessageId();
        }
        Message cursorMessage = messageMapper.selectById(lastMessageId);
        // 游标必须属于本会话：否则构造其它会话的 messageId 就能把未读一次性清零，并把 last_read_message_id 推到越权值。
        if (cursorMessage == null
                || !conversation.getId().equals(cursorMessage.getConversationId())) {
            throw new BusinessException("已读游标不属于当前会话");
        }
        return lastMessageId;
    }

    private ConversationCategory validateConversationType(String categoryCode, String messageType) {
        ConversationCategory category = ConversationCategory.fromCode(categoryCode);
        if (category == null || !category.getMessageType().equals(messageType)) {
            throw new BusinessException("会话分类与类型不匹配");
        }
        return category;
    }

    private void validateCategories(List<String> categories) {
        if (categories == null) {
            return;
        }
        boolean containsInvalidCategory = categories.stream()
                .anyMatch(category -> ConversationCategory.fromCode(category) == null);
        if (containsInvalidCategory) {
            throw new BusinessException("会话分类不合法");
        }
    }

    private PageResult<Conversation> conversationPage(Integer userId,
                                                      List<String> categories,
                                                      int page, int pageSize) {
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(pageSize);
        int offset = (safePage - 1) * safePageSize;
        // 总数与列表共用同一组过滤条件：count 也要带 categories，否则分页页码会多出空白页。
        return new PageResult<>(
                messageMapper.selectConversationSummaries(
                        userId, categories, offset, safePageSize),
                messageMapper.countConversationSummaries(userId, categories),
                safePage, safePageSize);
    }

    private String defaultContentType(String contentType) {
        return contentType == null || contentType.isBlank() ? "text" : contentType;
    }

    private String buildPreview(Message message) {
        String contentType = message.getContentType();
        String content = message.getContent() == null ? "" : message.getContent();
        if ("product_card".equals(contentType) || "service_card".equals(contentType)
                || "order_card".equals(contentType)) {
            // 卡片消息的 content 是 JSON 快照：会话列表只显示可读摘要，这里拼成「[商品]/[服务]/[订单] + name」再截断。
            String label = "product_card".equals(contentType) ? "[商品]"
                    : "service_card".equals(contentType) ? "[服务]" : "[订单]";
            String name = extractCardName(content);
            return truncatePreview(name.isBlank() ? label : label + " " + name);
        }
        return truncatePreview(content);
    }

    private String extractCardName(String content) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"name\"\\s*:\\s*\"([^\"]*)\"").matcher(content);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String truncatePreview(String content) {
        // 预览会写进 conversations.last_message：截断到固定长度，避免整段正文进入会话列表查询。
        return content.length() > MESSAGE_PREVIEW_LENGTH
                ? content.substring(0, MESSAGE_PREVIEW_LENGTH) : content;
    }

    private User requireChatUser(Integer userId) {
        User user = userMapper.selectById(userId);
        String role = user == null ? null : user.getRole();
        // 会话功能只对 normal 状态的普通用户和商家开放：账号被禁用后即使 token 未过期也不能继续收发。
        boolean allowedRole = AccountRole.USER.getCode().equals(role)
                || AccountRole.BUSINESS.getCode().equals(role);
        if (!allowedRole
                || !AccountStatus.NORMAL.getCode().equals(user == null ? null : user.getStatus())) {
            throw new BusinessException(403, "当前账号无权使用会话功能");
        }
        return user;
    }

    private void pushAfterCommit(List<Integer> userIds, String eventName, Object payload) {
        Runnable action = () -> userIds.forEach(userId ->
                conversationSseService.push(userId, eventName, payload));
        // 事务同步激活时注册到 afterCommit：SSE 不能早于提交，否则前端可能收到一条随后被回滚的消息；无事务时（如直接调用）立即推送。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
            return;
        }
        action.run();
    }
}
