package com.hnist.mapper;

import com.hnist.vo.UserSummaryResponse;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 用户关注关系数据访问。 */
public interface UserFollowMapper {

    int exists(@Param("followerId") Integer followerId,
               @Param("followeeId") Integer followeeId);

    /** INSERT IGNORE：重复关注同一人时命中唯一键被静默忽略，返回 0。 */
    int insert(@Param("followerId") Integer followerId,
               @Param("followeeId") Integer followeeId);

    int delete(@Param("followerId") Integer followerId,
               @Param("followeeId") Integer followeeId);

    int countFollowing(@Param("userId") Integer userId);

    int countFollowers(@Param("userId") Integer userId);

    List<UserSummaryResponse> selectFollowing(@Param("userId") Integer userId,
                                              @Param("offset") Integer offset,
                                              @Param("limit") Integer limit);

    List<UserSummaryResponse> selectFollowers(@Param("userId") Integer userId,
                                              @Param("offset") Integer offset,
                                              @Param("limit") Integer limit);

}
