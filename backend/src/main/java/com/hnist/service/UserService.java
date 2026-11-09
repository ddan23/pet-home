package com.hnist.service;

import com.hnist.vo.AddressesResponse;
import com.hnist.vo.FollowStatusResponse;
import com.hnist.vo.PetsResponse;
import com.hnist.dto.RegisterRequest;
import com.hnist.dto.UpdateProfileRequest;
import com.hnist.vo.UserPublicInfoResponse;
import com.hnist.vo.UserSummaryResponse;
import com.hnist.common.result.PageResult;
import com.hnist.entity.User;

/** 账号鉴权、注册改密与用户资料、关注关系维护。 */
public interface UserService {

    // 登录鉴权：成功返回用户，任何形式的失败都返回 null，不抛异常。
    User validate(String username, String password, String role);

    // 自助注册入口，只接受 user 与 business 两种角色。
    User register(RegisterRequest request);

    // 改密前必须校验当前密码，校验不过时不做任何写入。
    void changePassword(Integer userId, String currentPassword, String newPassword);

    User findById(Integer id);

    User getCurrentUserInfo(Integer userId);

    User updateProfile(Integer userId, UpdateProfileRequest request);

    // 宠物档案按 JSON 数组字符串返回，前端可直接解析。
    PetsResponse getPets(Integer userId);

    void savePets(Integer userId, String pets);

    AddressesResponse getAddresses(Integer userId);

    void saveAddresses(Integer userId, String addresses);

    // 对外主页资料是白名单裁剪后的结果，不是完整用户对象。
    UserPublicInfoResponse getPublicInfo(Integer userId);

    FollowStatusResponse checkFollow(Integer currentUserId, Integer targetUserId);

    PageResult<UserSummaryResponse> listFollowing(Integer userId, int page, int pageSize);

    PageResult<UserSummaryResponse> listFollowers(Integer userId, int page, int pageSize);

    // 关注与取关共用同一入口，返回操作后的关注状态。
    FollowStatusResponse toggleFollow(Integer currentUserId, Integer targetUserId);

    // 管理端分页；extra 里附带按 role 统计的 normal / disabled 总量，不受 keyword 影响。
    PageResult<User> listUsers(String role, String status, String keyword, int page, int pageSize);

    // 只接受 normal 或 disabled：用于停用与恢复账号，停用后 validate 会直接判定登录失败。
    void updateUserStatus(Integer id, String status);
}
