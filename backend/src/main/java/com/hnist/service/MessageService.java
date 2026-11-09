package com.hnist.service;

import com.hnist.dto.SendMessageRequest;
import com.hnist.vo.SendMessageResponse;
import com.hnist.dto.CreateConversationRequest;
import com.hnist.dto.SendConversationMessageRequest;
import com.hnist.vo.ConversationMessagePage;
import com.hnist.entity.Conversation;
import com.hnist.entity.Message;
import com.hnist.common.result.PageResult;

import java.util.List;
import java.util.Map;

/** 一对一会话服务。 */
public interface MessageService {

    /** 兼容旧版消息发送接口，内部统一走会话消息流程；clientMessageId 由服务端生成，因此不提供重发幂等。 */
    SendMessageResponse sendMessage(Integer senderId, SendMessageRequest request);

    /** 当前用户可见的会话分页；categories 为空表示不按分类过滤。 */
    PageResult<Conversation> listConversationSummaries(
            Integer userId, List<String> categories, int page, int pageSize);

    /** 商家侧会话列表：只允许 mall/service 两类，传入其它分类返回 403。 */
    PageResult<Conversation> listMerchantConversationSummaries(
            Integer userId, List<String> categories, int page, int pageSize);

    /** 按参与人视角取会话；非参与者或已删除会话抛 404。 */
    Conversation getConversationForUser(Integer conversationId, Integer userId);

    /** 开通或复用双方会话；分类与角色不匹配时拒绝，重复会话由 uk_user_target_cat 归一化收敛。 */
    Conversation openConversation(Integer userId, CreateConversationRequest request);

    /** 游标分页拉取历史消息；beforeId 为空取最新一页，返回结果按时间正序，limit 上限 100。 */
    ConversationMessagePage listConversationMessages(Integer conversationId, Integer userId,
                                                      Integer beforeId, int limit);

    /** 发送一条会话消息；clientMessageId 相同的重复请求返回原消息，不新增记录也不重复计未读。 */
    Message sendConversationMessage(Integer conversationId, Integer userId,
                                    SendConversationMessageRequest request);

    /** 推进已读游标并返回该用户全部会话的未读总数；lastMessageId 必须属于本会话。 */
    int markConversationRead(Integer conversationId, Integer userId,
                             Integer lastMessageId);

    /** 单方删除会话，并把当前最后一条消息设为清除水位，不影响对方视角。 */
    void deleteConversation(Integer conversationId, Integer userId);

    /** 未删除会话的未读总数，取自 conversation_participants.unread_count。 */
    int getConversationUnreadCount(Integer userId);

    /** 按会话分类聚合未读，无未读的分类补 0，保证前端 tab 有固定键。 */
    Map<String, Integer> getConversationUnreadCountsByCategory(Integer userId);

}
