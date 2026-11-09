package com.hnist.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** conversations 表行映射：一对一会话，other* 与 unreadCount 来自 conversation_participants 关联。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Conversation implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    /** 场景：community、mall、service */
    private String category;
    /** 与 category 固定配对：private_letter、customer_service、consult */
    private String type;
    private Integer userId;
    private Integer targetId;
    private String lastMessage;
    private Integer lastMessageId;
    private LocalDateTime lastMessageAt;
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
    /** 非本表列，取自当前用户 participant 的未读数 */
    private Integer unreadCount;
    /** 以下 other* 字段来自对方 participant 与 users、shops 关联，不是 conversations 的列 */
    private Integer otherUserId;
    private String otherUserName;
    private String otherUserAvatar;
    private String otherShopName;
    private String otherShopLogo;
    private Integer otherLastReadMessageId;
}
