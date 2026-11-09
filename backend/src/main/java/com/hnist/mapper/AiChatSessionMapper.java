package com.hnist.mapper;

import com.hnist.entity.AiChatSession;
import org.apache.ibatis.annotations.Param;

/** AI 养宠顾问会话数据访问。 */
public interface AiChatSessionMapper {

    AiChatSession selectByOwnerAndMode(@Param("userId") Integer userId,
                                       @Param("petId") Integer petId,
                                       @Param("mode") String mode);

    AiChatSession selectByIdAndUserId(@Param("id") Long id,
                                      @Param("userId") Integer userId);

    int insert(AiChatSession session);

    int touch(@Param("id") Long id);

    int deleteByIdAndUserId(@Param("id") Long id,
                            @Param("userId") Integer userId);
}
