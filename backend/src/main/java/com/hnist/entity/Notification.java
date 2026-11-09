package com.hnist.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** notifications 表行映射：站内通知，接收人在实体里命名为 receiverId。 */
@Data
public class Notification implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 接收人，对应表列 user_id */
    private Integer receiverId;
    private Integer senderId;
    /** 分类：system、community、mall、service、health */
    private String category;
    /** 事件类型，必须落在 category 的允许清单内（NotificationCategory） */
    private String type;
    /** 级别，默认 info */
    private String level;
    private String title;
    private String content;
    private String relatedType;
    private Long relatedId;
    private String actionUrl;
    /** 去重键，与 user_id 组成唯一索引 */
    private String dedupeKey;
    /** 是否已读：库字段 is_read，属性名去掉 is 前缀，对外 JSON 仍保持 isRead。 */
    private Boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    /** 非DB字段，关联 users.nickname */
    private String senderName;
    /** 非DB字段，关联 users.avatar */
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
