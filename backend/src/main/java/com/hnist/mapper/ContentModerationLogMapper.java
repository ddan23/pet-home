package com.hnist.mapper;

import com.hnist.entity.ContentModerationLog;
import org.apache.ibatis.annotations.Param;

/** 内容审核日志 Mapper。 */
public interface ContentModerationLogMapper {

    int insert(ContentModerationLog log);

    ContentModerationLog selectLatest(@Param("targetType") String targetType,
                                      @Param("targetId") Long targetId);

    /** 条件更新：仅 review_status = 'pending' 的记录可写复审结果，返回 0 表示已被处理过，防止并发复审互相覆盖。 */
    int updateReviewStatus(@Param("targetType") String targetType,
                           @Param("targetId") Long targetId,
                           @Param("reviewerId") Integer reviewerId,
                           @Param("reviewStatus") String reviewStatus);
}
