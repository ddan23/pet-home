package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 私信会话的分类未读数，一行对应一个 ConversationCategory。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationUnreadCount {

    /** 会话分类：community、mall、service；SQL 只返回真正有未读的分类，缺失的分类由服务层补 0。 */
    private String category;

    private Integer unreadCount;
}
