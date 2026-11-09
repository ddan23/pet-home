package com.hnist.mapper;

import com.hnist.entity.Conversation;
import com.hnist.entity.Message;
import com.hnist.vo.ConversationUnreadCount;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 一对一会话数据访问层。 */
public interface MessageMapper {

    Message selectById(@Param("id") Integer id);

    int insert(Message message);

    /** 幂等建会话：命中唯一键时通过 ON DUPLICATE KEY UPDATE 返回已有会话 id，不新建。 */
    int insertConversation(@Param("conversation") Conversation conversation);

    Integer lockConversationForUpdate(@Param("conversationId") Integer conversationId);

    Conversation selectConversationByPair(@Param("userId") Integer userId,
                                          @Param("targetId") Integer targetId,
                                          @Param("category") String category);

    List<Conversation> selectConversationSummaries(@Param("userId") Integer userId,
                                                    @Param("categories") List<String> categories,
                                                    @Param("offset") Integer offset,
                                                    @Param("limit") Integer limit);

    int countConversationSummaries(@Param("userId") Integer userId,
                                   @Param("categories") List<String> categories);

    Conversation selectConversationForUser(@Param("conversationId") Integer conversationId,
                                            @Param("userId") Integer userId);

    /** INSERT IGNORE：参与者重复加入时被忽略，保证幂等。 */
    int insertParticipant(@Param("conversationId") Integer conversationId,
                          @Param("userId") Integer userId);

    List<Integer> selectParticipantIds(@Param("conversationId") Integer conversationId);

    /** 按 client_message_id 加 FOR UPDATE 查重，防止客户端重试导致重复发消息。 */
    Message selectByClientMessageId(@Param("conversationId") Integer conversationId,
                                    @Param("senderId") Integer senderId,
                                    @Param("clientMessageId") String clientMessageId);

    List<Message> selectLatestMessages(@Param("conversationId") Integer conversationId,
                                       @Param("afterMessageId") Integer afterMessageId,
                                       @Param("beforeId") Integer beforeId,
                                       @Param("limit") int limit);

    int incrementParticipantUnread(@Param("conversationId") Integer conversationId,
                                   @Param("userId") Integer userId);

    int markConversationMessagesRead(@Param("conversationId") Integer conversationId,
                                     @Param("userId") Integer userId,
                                     @Param("lastMessageId") Integer lastMessageId);

    int updateParticipantRead(@Param("conversationId") Integer conversationId,
                              @Param("userId") Integer userId,
                              @Param("lastMessageId") Integer lastMessageId);

    int sumConversationUnread(@Param("userId") Integer userId);

    List<ConversationUnreadCount> selectConversationUnreadCountsByCategory(
            @Param("userId") Integer userId);

    int updateConversationLastMessage(@Param("conversationId") Integer conversationId,
                                      @Param("messageId") Integer messageId,
                                      @Param("preview") String preview,
                                      @Param("messageAt") LocalDateTime messageAt);

    Integer selectClearedBeforeMessageId(@Param("conversationId") Integer conversationId,
                                         @Param("userId") Integer userId);

    // 软隐藏会话：置参与者 deleted_at 并清零未读；cleared_before_message_id 只增不减（GREATEST 合并）
    int hideConversation(@Param("conversationId") Integer conversationId,
                         @Param("userId") Integer userId,
                         @Param("clearedBeforeMessageId") Integer clearedBeforeMessageId);

    int restoreConversation(@Param("conversationId") Integer conversationId,
                            @Param("userId") Integer userId);
}
