package com.hnist.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** messages 表行映射：会话内消息，category 与 type 必须与所属会话保持一致。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer senderId;
    private Integer receiverId;
    /** 会话场景：community、mall、service */
    private String category;
    /** 与 category 固定配对：private_letter、customer_service、consult */
    private String type;
    /** 所属会话ID，非空 */
    private Integer conversationId;
    private String title;
    private String content;
    /** text、image、product_card、service_card、order_card、system */
    private String contentType;
    private String relatedType;
    private Long relatedId;
    /** 客户端幂等键，与 conversation_id、sender_id 组成唯一索引 */
    private String clientMessageId;
    /** 是否已读：库字段 is_read，属性名去掉 is 前缀，对外 JSON 仍保持 isRead。 */
    private Boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    /** 非DB字段 */
    private String senderName;
    /** 非DB字段 */
    private String senderAvatar;
    /** 非DB字段，商家店铺名 */
    private String senderShopName;
    /** 非DB字段，商家店铺Logo */
    private String senderShopLogo;

    /** 对外仍输出 isRead：旧前端按该字段判断已读态，改字段名不能改 JSON 契约。 */
    @JsonProperty("isRead")
    public Boolean getRead() {
        return read;
    }

    @JsonProperty("isRead")
    public void setRead(Boolean read) {
        this.read = read;
    }
}
