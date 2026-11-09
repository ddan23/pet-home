package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 发送私信后的回执：告知落在哪个会话、哪条消息，前端据此追加气泡而不必重新拉列表。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageResponse {

    private Integer conversationId;

    private Integer messageId;

    /** 会话分类：community、mall、service，决定消息归属的入口。 */
    private String category;

    /** 消息类型，与分类固定绑定（private_letter、customer_service、consult）。 */
    private String type;
}
