package com.hnist.dto;

import lombok.Data;

/** 标记会话已读的游标。 */
@Data
public class MarkConversationReadRequest {

    /** 为空表示读到会话最新一条；非空时服务端校验该消息属于本会话，游标只前进不回退。 */
    private Integer lastMessageId;
}
