package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 消息中心未读摘要：站内通知与私信会话两套统计并列，前端底部角标用 grandTotal。 */
@Data
@AllArgsConstructor
public class UnreadSummaryResponse {

    /** 各分类通知未读之和，不含私信。 */
    private Integer total;

    private Integer community;

    private Integer mall;

    private Integer service;

    private Integer health;

    private Integer system;

    /** 各分类私信未读之和，不含通知。 */
    private Integer conversations;

    private Integer communityConversations;

    private Integer mallConversations;

    private Integer serviceConversations;

    /** total + conversations，通知与会话键集合不同且互不重叠，可直接相加。 */
    private Integer grandTotal;
}
