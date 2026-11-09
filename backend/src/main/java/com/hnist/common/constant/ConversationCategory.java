package com.hnist.common.constant;

import java.util.Arrays;

/** 一对一会话场景，code 落 category、messageType 落 type，两列必须成对匹配才能通过约束。 */
public enum ConversationCategory {

    /** 社区私信：用户之间围绕帖子/关注发起，商家后台默认查不到该类会话。 */
    COMMUNITY("community", "private_letter"),
    /** 商城客服：用户与商家旗舰店之间的咨询，商家侧可见。 */
    MALL("mall", "customer_service"),
    /** 服务咨询：用户与线下服务门店之间，预约相关沟通走这里。 */
    SERVICE("service", "consult");

    private final String code;
    private final String messageType;

    ConversationCategory(String code, String messageType) {
        this.code = code;
        this.messageType = messageType;
    }

    public String getCode() {
        return code;
    }

    public String getMessageType() {
        return messageType;
    }

    /** 按分类 code 精确匹配，未知值返回 null，由调用方决定抛错还是按空处理。 */
    public static ConversationCategory fromCode(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    /** 兼容旧接口以消息类型当别名的入参（private_letter/customer_service/consult），同时接受分类 code。 */
    public static ConversationCategory fromAlias(String alias) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(alias) || item.messageType.equals(alias))
                .findFirst()
                .orElse(null);
    }

    /** 校验分类与类型是否取自同一条枚举：两列都落库，错配会撞 messages 表的场景检查约束。 */
    public static boolean isValidPair(String category, String messageType) {
        ConversationCategory item = fromCode(category);
        return item != null && item.messageType.equals(messageType);
    }
}
