package com.hnist.service.impl;

import com.hnist.common.exception.BusinessException;
import com.hnist.common.constant.AccountRole;
import com.hnist.common.constant.AccountStatus;
import com.hnist.mapper.PostMapper;
import com.hnist.mapper.ShopMapper;
import com.hnist.mapper.UserFollowMapper;
import com.hnist.mapper.UserMapper;
import com.hnist.vo.AddressesResponse;
import com.hnist.vo.FollowStatusResponse;
import com.hnist.vo.PetsResponse;
import com.hnist.dto.RegisterRequest;
import com.hnist.dto.UpdateProfileRequest;
import com.hnist.vo.UserPublicInfoResponse;
import com.hnist.vo.UserSummaryResponse;
import com.hnist.common.result.PageResult;
import com.hnist.entity.Shop;
import com.hnist.entity.User;
import com.hnist.event.NotificationEvent;
import com.hnist.event.NotificationEventPublisher;
import com.hnist.service.UserService;
import com.hnist.service.PetService;
import com.hnist.common.util.PageUtils;
import com.hnist.support.ShippingAddressValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DuplicateKeyException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 账号鉴权、注册改密以及用户资料与关注关系的维护。 */
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;
    private final ShopMapper shopMapper;
    private final PostMapper postMapper;
    private final NotificationEventPublisher notificationPublisher;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final PetService petService;

    public UserServiceImpl(UserMapper userMapper, UserFollowMapper userFollowMapper,
                           ShopMapper shopMapper, PostMapper postMapper,
                           NotificationEventPublisher notificationPublisher, ObjectMapper objectMapper,
                           PasswordEncoder passwordEncoder, PetService petService) {
        this.userMapper = userMapper;
        this.userFollowMapper = userFollowMapper;
        this.shopMapper = shopMapper;
        this.postMapper = postMapper;
        this.notificationPublisher = notificationPublisher;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.petService = petService;
    }

    @Override
    public User validate(String username, String password, String role) {
        User user = userMapper.selectByUsername(username);
        // 账号不存在、角色与登录端不符、账号被停用都返回 null：调用方统一回 401 同一句提示，错误文案不能暴露账号是否存在。
        if (user == null || !user.getRole().equals(role)
                || !AccountStatus.NORMAL.getCode().equals(user.getStatus())) {
            return null;
        }
        String storedPassword = user.getPassword();
        if (storedPassword == null) {
            return null;
        }
        // 库里既有 BCrypt 哈希也有历史明文：$2 前缀判定算法，命中明文时立刻用 BCrypt(12) 回写升级，明文不会长期留存。
        if (storedPassword.startsWith("$2")) {
            return passwordEncoder.matches(password, storedPassword) ? user : null;
        }
        if (storedPassword.equals(password)) {
            userMapper.updatePassword(user.getId(), passwordEncoder.encode(password));
            return user;
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User register(RegisterRequest request) {
        String role = request.getRole();
        // 自助注册只开放普通用户与商家：admin 账号不能通过注册接口产生，否则任何人都能注册出后台账号。
        if (!AccountRole.USER.getCode().equals(role)
                && !AccountRole.BUSINESS.getCode().equals(role)) {
            throw new BusinessException("只能注册普通用户或商家账号");
        }
        String username = request.getUsername().trim();
        if (userMapper.selectByUsername(username) != null) {
            throw new BusinessException("用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        // 明文密码只存在于请求体，落库统一换成 BCrypt 哈希，任何环节都不保留可复用的口令原文。
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setNickname(request.getNickname().trim());
        // 手机号不做唯一性校验：users 表只有 uk_username 一个唯一索引，同一个手机号可以被多个账号使用。
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setCity(request.getCity());
        user.setStatus(AccountStatus.NORMAL.getCode());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            // 上面的查重挡不住并发：同名同时提交时由 uk_username 抛错，这里翻译成同一句提示，不把数据库异常透给前端。
            throw new BusinessException("用户名已存在");
        }
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Integer userId, String currentPassword, String newPassword) {
        User user = requireUser(userId);
        String storedPassword = user.getPassword();
        // 改密必须再验一次旧密码，不能只凭已登录会话：会话凭据被拿走时，没有原密码依然改不了密码。历史明文账号同样走这条分支。
        boolean validCurrent = storedPassword != null && (storedPassword.startsWith("$2")
                ? passwordEncoder.matches(currentPassword, storedPassword)
                : storedPassword.equals(currentPassword));
        if (!validCurrent) {
            throw new BusinessException("当前密码不正确");
        }
        // 新旧相同只能交给编码器比对（哈希不可逆）；历史明文账号的 storedPassword 不是 BCrypt 格式，此处 matches 恒为 false，这条校验对它们不生效。
        if (passwordEncoder.matches(newPassword, storedPassword)) {
            throw new BusinessException("新密码不能与当前密码相同");
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
    }

    @Override
    public User findById(Integer id) {
        return userMapper.selectById(id);
    }

    @Override
    public User getCurrentUserInfo(Integer userId) {
        User user = requireUser(userId);
        // 帖子数、关注数、粉丝数不在 users 表里，由各自 mapper 单独计数后回填，只用于个人主页展示，不参与写回。
        user.setPostCount(postMapper.countByUserId(userId, null));
        user.setFollowCount(userFollowMapper.countFollowing(userId));
        user.setFanCount(userFollowMapper.countFollowers(userId));
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User updateProfile(Integer userId, UpdateProfileRequest request) {
        User user = requireUser(userId);
        String nickname = trimToNull(request.getNickname());
        if (nickname == null) {
            throw new BusinessException("昵称不能为空");
        }
        user.setNickname(nickname);
        // 手机号、邮箱、城市为 null 表示本次不改：避免前端只改昵称时把已有联系方式整片清空。手机号无唯一约束，直接覆盖。
        if (request.getPhone() != null) {
            user.setPhone(trimToNull(request.getPhone()));
        }
        if (request.getEmail() != null) {
            user.setEmail(trimToNull(request.getEmail()));
        }
        if (request.getCity() != null) {
            user.setCity(trimToNull(request.getCity()));
        }
        // 影响行数为 0 说明账号已被删除，按保存失败返回，不能假装更新成功。
        if (userMapper.updateProfile(user) == 0) {
            throw new BusinessException("保存失败");
        }
        return user;
    }

    @Override
    public PetsResponse getPets(Integer userId) {
        // 宠物档案以 pets 表为准，users.pets 只是历史 JSON 快照：读写统一委托 PetService，整份覆盖与内部事务也由它负责。
        return new PetsResponse(petService.getPetsJson(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void savePets(Integer userId, String pets) {
        petService.savePetsJson(userId, pets);
    }

    @Override
    public AddressesResponse getAddresses(Integer userId) {
        User user = requireUser(userId);
        // addresses 为空时返回 "[]"：前端按数组解析，不用为没存过地址的老账号单独判空。
        return new AddressesResponse(user.getAddresses() == null ? "[]" : user.getAddresses());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAddresses(Integer userId, String addresses) {
        // 落库前先整份校验（收货人、手机号、省市区、详细地址），非法 JSON 不进库；地址簿是整份覆盖，不做增量合并。
        ShippingAddressValidator.validateList(addresses, objectMapper);
        if (userMapper.updateAddresses(userId, addresses) == 0) {
            throw new BusinessException("保存失败");
        }
    }

    @Override
    public UserPublicInfoResponse getPublicInfo(Integer userId) {
        User user = requireUser(userId);
        // 对外资料只回填 id、昵称、头像、角色：phone、email、addresses 不进入 UserPublicInfoResponse，主页拿不到联系方式。
        UserPublicInfoResponse response = new UserPublicInfoResponse();
        response.setId(user.getId());
        response.setNickname(user.getNickname());
        response.setAvatar(user.getAvatar());
        response.setRole(user.getRole());
        // 商家对外展示门店形象：优先取旗舰店，没有旗舰店时退回任一有效门店。
        if (AccountRole.BUSINESS.getCode().equals(user.getRole())) {
            Shop shop = shopMapper.selectFlagshipByOwner(userId);
            if (shop == null) {
                shop = shopMapper.selectByOwnerId(userId);
            }
            // 商家自己没设头像时用门店 logo 兜底，避免商家主页出现空白头像。
            if (shop != null && shop.getLogo() != null && !shop.getLogo().isBlank()) {
                response.setShopLogo(shop.getLogo());
                response.setShopName(shop.getName());
                if (user.getAvatar() == null || user.getAvatar().isBlank()) {
                    response.setAvatar(shop.getLogo());
                }
            }
        }
        return response;
    }

    @Override
    public FollowStatusResponse checkFollow(Integer currentUserId, Integer targetUserId) {
        // 未登录访问（currentUserId 为 null）直接返回未关注，不抛异常：用户主页对匿名访客要可读。
        if (currentUserId == null) {
            return new FollowStatusResponse(false);
        }
        return new FollowStatusResponse(
                userFollowMapper.exists(currentUserId, targetUserId) > 0);
    }

    @Override
    public PageResult<UserSummaryResponse> listFollowing(Integer userId, int page, int pageSize) {
        requireUser(userId);
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(pageSize);
        int offset = (safePage - 1) * safePageSize;
        return new PageResult<>(
                userFollowMapper.selectFollowing(userId, offset, safePageSize),
                userFollowMapper.countFollowing(userId), safePage, safePageSize);
    }

    @Override
    public PageResult<UserSummaryResponse> listFollowers(Integer userId, int page, int pageSize) {
        requireUser(userId);
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(pageSize);
        int offset = (safePage - 1) * safePageSize;
        return new PageResult<>(
                userFollowMapper.selectFollowers(userId, offset, safePageSize),
                userFollowMapper.countFollowers(userId), safePage, safePageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowStatusResponse toggleFollow(Integer currentUserId, Integer targetUserId) {
        // 自我关注没有业务意义，直接拒绝，否则粉丝数会凭空加一。
        if (currentUserId.equals(targetUserId)) {
            throw new BusinessException("不能关注自己");
        }
        User currentUser = requireUser(currentUserId);
        User targetUser = requireUser(targetUserId);
        boolean alreadyFollowing = userFollowMapper.exists(currentUserId, targetUserId) > 0;
        boolean relationChanged;
        // 关注与取关共用同一入口：先用 exists 数一下当前关系，再决定删还是插，返回值表示操作后的关注状态。
        if (alreadyFollowing) {
            relationChanged = userFollowMapper.delete(currentUserId, targetUserId) > 0;
        } else {
            relationChanged = userFollowMapper.insert(currentUserId, targetUserId) > 0;
        }
        // 只有本次真的新增关注才发通知：取关、重复点击都不产生消息，避免被反复点关注刷通知。
        if (!alreadyFollowing && relationChanged) {
            String nickname = currentUser.getNickname() == null
                    ? currentUser.getUsername() : currentUser.getNickname();
            notificationPublisher.publish(new NotificationEvent(
                    targetUserId, currentUserId, "community", "follow", "info",
                    "新的关注", nickname + " 关注了你", "user",
                    currentUserId.longValue(), "/messages/" + currentUserId + "?category=community",
                    null));
        }
        return new FollowStatusResponse(!alreadyFollowing);
    }

    @Override
    public PageResult<User> listUsers(String role, String status, String keyword, int page, int pageSize) {
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(pageSize);
        int offset = (safePage - 1) * safePageSize;
        // 管理端直接返回 User 实体：password 由实体上的 @JsonIgnore 挡在序列化之外，手机号等联系方式保持原值，不做打码。
        List<User> users = userMapper.selectAll(role, status, keyword, offset, safePageSize);
        int total = userMapper.countAll(role, status, keyword);
        PageResult<User> result = new PageResult<>(users, total, safePage, safePageSize);
        // 顶部统计数字要与列表筛选解耦：只按 role 统计 normal / disabled 总量，不带 keyword，换搜索词时数字不跟着跳。
        Map<String, Object> extra = new HashMap<>();
        extra.put("normalCount", userMapper.countByRoleAndStatus(role, "normal"));
        extra.put("disabledCount", userMapper.countByRoleAndStatus(role, "disabled"));
        result.setExtra(extra);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Integer id, String status) {
        // 管理端只在 normal / disabled 之间切换，其余值一律拒绝；先 requireUser 保证对不存在的账号返回 404 而不是静默成功。
        if (!AccountStatus.NORMAL.getCode().equals(status)
                && !AccountStatus.DISABLED.getCode().equals(status)) {
            throw new BusinessException("用户状态不合法");
        }
        requireUser(id);
        userMapper.updateStatus(id, status);
    }

    private User requireUser(Integer userId) {
        // 按 id 取用户并要求存在的统一出口：缺失即 404，避免各方法各自返回 null 让上层漏判。
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
