package com.hnist.mapper;

import com.hnist.vo.MyCommentResponse;
import com.hnist.entity.PostComment;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 帖子评论数据访问。 */
public interface PostCommentMapper {

    PostComment selectById(@Param("id") Long id);

    List<PostComment> selectByPostId(@Param("postId") Integer postId,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    int countByPostId(@Param("postId") Integer postId);

    List<PostComment> selectByPostIdForAdmin(@Param("postId") Integer postId,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    int countByPostIdForAdmin(@Param("postId") Integer postId);

    List<PostComment> selectPending(@Param("offset") int offset,
                                    @Param("limit") int limit);

    int countPending();

    List<PostComment> selectForAdmin(@Param("status") String status,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    int countForAdmin(@Param("status") String status);

    List<MyCommentResponse> selectMyComments(@Param("userId") Integer userId,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    int countMyComments(@Param("userId") Integer userId);

    int insert(PostComment comment);

    int deleteById(@Param("id") Long id);

    // 仅 status = 'pending' 且未删除的评论可改状态，返回 0 表示已审核过，防止重复处理
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status);

    // CAS 条件更新：仅当当前状态等于 currentStatus 时才变更，避免并发审核互相覆盖
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("currentStatus") String currentStatus,
                              @Param("status") String status);

    // 管理员改状态：status 与目标不同才会更新，相同则返回 0（幂等）
    int updateStatusByAdmin(@Param("id") Long id,
                            @Param("status") String status);
}
