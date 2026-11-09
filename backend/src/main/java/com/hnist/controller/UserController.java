package com.hnist.controller;

import com.hnist.common.constant.AccountRole;

import com.hnist.annotation.CurrentUser;
import com.hnist.annotation.RequireRole;
import com.hnist.common.result.Result;
import com.hnist.support.DataMasking;
import com.hnist.vo.AddressesResponse;
import com.hnist.vo.FollowStatusResponse;
import com.hnist.vo.PetsResponse;
import com.hnist.dto.SaveAddressesRequest;
import com.hnist.dto.SavePetsRequest;
import com.hnist.dto.UpdateProfileRequest;
import com.hnist.dto.UpdateStatusRequest;
import com.hnist.vo.UserPublicInfoResponse;
import com.hnist.vo.UserSummaryResponse;
import com.hnist.common.result.PageResult;
import com.hnist.entity.User;
import com.hnist.service.UserService;
import com.hnist.service.AuditLogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 用户资料、关注关系与用户管理控制器。 */
@RestController
@RequestMapping({"/api", "/api/v1"})
public class UserController {

    private final UserService userService;
    private final AuditLogService auditLogService;

    public UserController(UserService userService, AuditLogService auditLogService) {
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/user/info")
    @RequireRole(AccountRole.USER_CODE)
    public Result<User> getCurrentUser(@CurrentUser User user) {
        return Result.ok(userService.getCurrentUserInfo(user.getId()));
    }

    @PutMapping("/user/profile")
    @RequireRole(AccountRole.USER_CODE)
    public Result<User> updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                      @CurrentUser User user) {
        return Result.ok("保存成功", userService.updateProfile(user.getId(), request));
    }

    @GetMapping("/user/pets")
    @RequireRole(AccountRole.USER_CODE)
    public Result<PetsResponse> getPets(@CurrentUser User user) {
        return Result.ok(userService.getPets(user.getId()));
    }

    @PutMapping("/user/pets")
    @RequireRole(AccountRole.USER_CODE)
    public Result<Void> savePets(@Valid @RequestBody SavePetsRequest request,
                                 @CurrentUser User user) {
        userService.savePets(user.getId(), request.getPets());
        return Result.ok("保存成功", null);
    }

    @GetMapping("/user/addresses")
    @RequireRole(AccountRole.USER_CODE)
    public Result<AddressesResponse> getAddresses(@CurrentUser User user) {
        return Result.ok(userService.getAddresses(user.getId()));
    }

    @PutMapping("/user/addresses")
    @RequireRole(AccountRole.USER_CODE)
    public Result<Void> saveAddresses(@Valid @RequestBody SaveAddressesRequest request,
                                      @CurrentUser User user) {
        userService.saveAddresses(user.getId(), request.getAddresses());
        return Result.ok("保存成功", null);
    }

    @GetMapping("/user/info/other/{userId}")
    public Result<UserPublicInfoResponse> getPublicUserInfo(@PathVariable Integer userId) {
        return Result.ok(userService.getPublicInfo(userId));
    }

    @GetMapping("/user/follow/{targetUserId}")
    public Result<FollowStatusResponse> checkFollow(@PathVariable Integer targetUserId,
                                                    @CurrentUser(required = false) User user) {
        return Result.ok(userService.checkFollow(user == null ? null : user.getId(), targetUserId));
    }

    @GetMapping("/user/following")
    @RequireRole(AccountRole.USER_CODE)
    public Result<PageResult<UserSummaryResponse>> listFollowing(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
            @CurrentUser User user) {
        return Result.ok(userService.listFollowing(user.getId(), page, pageSize));
    }

    @GetMapping("/user/followers")
    @RequireRole(AccountRole.USER_CODE)
    public Result<PageResult<UserSummaryResponse>> listFollowers(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
            @CurrentUser User user) {
        return Result.ok(userService.listFollowers(user.getId(), page, pageSize));
    }

    @PostMapping("/user/follow/{targetUserId}")
    @RequireRole(AccountRole.USER_CODE)
    public Result<FollowStatusResponse> toggleFollow(@PathVariable Integer targetUserId,
                                                     @CurrentUser User user) {
        FollowStatusResponse response = userService.toggleFollow(user.getId(), targetUserId);
        return Result.ok(Boolean.TRUE.equals(response.getFollowing()) ? "关注成功" : "已取消关注", response);
    }

    @GetMapping("/admin/users")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<PageResult<User>> listUsers(
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        PageResult<User> result = userService.listUsers(role, status, keyword, page, pageSize);
        // 管理端查看的是他人资料：手机号与邮箱必须脱敏，避免后台截图或导出泄露完整联系方式。
        result.getList().forEach(this::maskSensitive);
        return Result.ok(result);
    }

    @GetMapping("/admin/users/{id}")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<User> getUserDetail(@PathVariable Integer id) {
        User user = userService.findById(id);
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }
        maskSensitive(user);
        return Result.ok(user);
    }

    /** 只作用于管理端返回体：对象为本次查询新加载，脱敏不会回写数据库。 */
    private void maskSensitive(User user) {
        if (user == null) {
            return;
        }
        user.setPhone(DataMasking.maskPhone(user.getPhone()));
        user.setEmail(DataMasking.maskEmail(user.getEmail()));
    }

    @PutMapping("/admin/users/{id}/status")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<Void> updateUserStatus(@PathVariable Integer id,
                                         @Valid @RequestBody UpdateStatusRequest request,
                                         @CurrentUser User operator) {
        userService.updateUserStatus(id, request.getStatus());
        auditLogService.record(operator.getId(), "USER_STATUS", "USER", id.longValue(),
                Map.of("status", request.getStatus()));
        return Result.ok();
    }
}
