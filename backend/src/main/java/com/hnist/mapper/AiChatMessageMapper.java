package com.hnist.mapper;

import com.hnist.entity.AiChatMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** AI 养宠顾问消息数据访问。 */
public interface AiChatMessageMapper {

    int insert(AiChatMessage message);

    AiChatMessage selectById(@Param("id") Long id);

    /** 先按 id 倒序取最新 limit 条，再翻转为正序返回，用于拼装对话上下文。 */
    List<AiChatMessage> selectRecent(@Param("sessionId") Long sessionId,
                                     @Param("limit") int limit);

    int deleteBySessionId(@Param("sessionId") Long sessionId);
}
