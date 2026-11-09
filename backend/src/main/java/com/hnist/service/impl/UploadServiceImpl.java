package com.hnist.service.impl;

import com.hnist.common.exception.BusinessException;
import com.hnist.common.constant.ShopType;
import com.hnist.config.UploadProperties;
import com.hnist.entity.Shop;
import com.hnist.mapper.ShopMapper;
import com.hnist.mapper.UserMapper;
import com.hnist.service.UploadService;
import com.hnist.support.ImageFileValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** 用户与商家图片上传：统一做类型白名单校验、配额控制与失败补偿清理。 */
@Slf4j
@Service
public class UploadServiceImpl implements UploadService {

    private static final String AVATAR_DIRECTORY = "avatar";
    private static final String POST_DIRECTORY = "post";
    private static final String LOGO_DIRECTORY = "logo";
    private static final String PET_DIRECTORY = "pet";
    private static final String MALL_DIRECTORY = ShopType.MALL.getCode();
    private static final String SERVICE_DIRECTORY = ShopType.SERVICE.getCode();
    // 落库只存 /uploads/ 开头的相对 URL，不存磁盘绝对路径：部署目录变化时历史图片地址仍然有效。
    private static final String UPLOAD_URL_PREFIX = "/uploads/";
    // 随机段取 8 位十六进制：配合用户/店铺前缀已足够避免重名，同时保持文件名可读、URL 短。
    private static final int RANDOM_NAME_LENGTH = 8;
    // 用户侧三类目录共享 user-quota-bytes 额度。
    private static final List<String> USER_UPLOAD_DIRECTORIES =
            List.of(AVATAR_DIRECTORY, POST_DIRECTORY, PET_DIRECTORY);
    // 商家侧三类目录共享 merchant-quota-bytes 额度。
    private static final List<String> MERCHANT_UPLOAD_DIRECTORIES =
            List.of(LOGO_DIRECTORY, MALL_DIRECTORY, SERVICE_DIRECTORY);

    private final UserMapper userMapper;
    private final ShopMapper shopMapper;
    private final UploadProperties uploadProperties;

    public UploadServiceImpl(UserMapper userMapper, ShopMapper shopMapper,
                             UploadProperties uploadProperties) {
        this.userMapper = userMapper;
        this.shopMapper = shopMapper;
        this.uploadProperties = uploadProperties;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadAvatar(Integer userId, MultipartFile file) {
        String path = null;
        try {
            path = saveImage(file, AVATAR_DIRECTORY, "avatar_" + userId,
                    userPrefixes(userId), uploadProperties.getUserQuotaBytes());
            // 文件先落盘再改 users.avatar；更新影响行数为 0 说明用户已不存在，抛异常走下面的补偿删除。
            if (userMapper.updateAvatar(userId, path) == 0) {
                throw new BusinessException(404, "用户不存在");
            }
            return path;
        } catch (RuntimeException exception) {
            // 头像写库失败要删掉刚写入的图片，否则每次失败都会留下一个没人引用的孤儿文件并继续占用配额。
            deleteImageQuietly(path);
            throw exception;
        }
    }

    @Override
    public String uploadPostImage(Integer userId, MultipartFile file) {
        return saveImage(file, POST_DIRECTORY, "post_" + userId,
                userPrefixes(userId), uploadProperties.getUserQuotaBytes());
    }

    @Override
    public String uploadProductImage(Integer ownerId, Integer shopId, MultipartFile file) {
        // shopId 由控制器经 getOpenFlagship 从登录态推导后传入，这里不再重复校验归属，只把它写进文件名做额度归属。
        return saveImage(file, MALL_DIRECTORY, "mall_" + shopId,
                merchantPrefixes(ownerId), uploadProperties.getMerchantQuotaBytes());
    }

    @Override
    public String uploadServiceImage(Integer ownerId, Integer shopId, MultipartFile file) {
        return saveImage(file, SERVICE_DIRECTORY, "service_" + shopId,
                merchantPrefixes(ownerId), uploadProperties.getMerchantQuotaBytes());
    }

    @Override
    public String uploadLogo(Integer userId, MultipartFile file) {
        return saveImage(file, LOGO_DIRECTORY, "logo_" + userId,
                merchantPrefixes(userId), uploadProperties.getMerchantQuotaBytes());
    }

    @Override
    public String uploadPetImage(Integer userId, MultipartFile file) {
        return saveImage(file, PET_DIRECTORY, "pet_" + userId,
                userPrefixes(userId), uploadProperties.getUserQuotaBytes());
    }

    private synchronized String saveImage(MultipartFile file, String category,
                                          String prefix, List<String> ownedPrefixes,
                                          long quotaBytes) {
        // 校验返回的是服务端认可的扩展名（扩展名白名单 + 文件头魔数都必须匹配），用户给的原文件名到这里就不再使用。
        String extension = ImageFileValidator.validate(file);
        // 已用空间按"目录 + 文件名前缀"扫描累加，因此文件名必须带归属前缀，否则这部分占用算不进任何人的配额。
        long usedBytes = calculateUsedBytes(categoryDirectories(category), ownedPrefixes);
        // 配额判断和写文件都在这一个 synchronized 方法里：并发上传不能同时通过检查把总额度撑爆；该锁只在单实例内有效。
        if (usedBytes + file.getSize() > quotaBytes) {
            throw new BusinessException(413, "图片存储空间已达上限");
        }
        // 文件名全部由服务端拼：前缀（常量 + 数字 id）+ 随机段 + 白名单扩展名，用户可控内容不进入路径。
        String randomPart = UUID.randomUUID().toString().replace("-", "")
                .substring(0, RANDOM_NAME_LENGTH);
        String filename = prefix + "_" + randomPart + extension;
        Path targetDirectory = Path.of(uploadProperties.getRoot()).toAbsolutePath().normalize()
                .resolve(category);
        Path target = targetDirectory.resolve(filename).normalize();
        // 归一化后必须仍在目标目录内：一旦扩展名或前缀被注入 ../，这里拦截而不是写到上传目录之外。
        if (!target.startsWith(targetDirectory)) {
            throw new BusinessException("文件路径不合法");
        }
        try {
            Files.createDirectories(targetDirectory);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return UPLOAD_URL_PREFIX + category + "/" + filename;
        } catch (IOException exception) {
            // 写盘中途失败可能已经留下半截文件，先清掉再抛错，避免残片被算进配额却无法访问。
            deleteImageQuietly(target);
            log.error("保存上传图片失败，目标路径：{}", target, exception);
            throw new BusinessException(500, "图片上传失败");
        }
    }

    private List<String> userPrefixes(Integer userId) {
        return List.of("avatar_" + userId, "post_" + userId, "pet_" + userId);
    }

    private List<String> merchantPrefixes(Integer ownerId) {
        List<String> result = new ArrayList<>();
        result.add("logo_" + ownerId);
        // 额度按商家名下全部门店合计，只用旗舰店前缀统计的话，多开一家门店就能再拿一份额度。
        for (Shop shop : shopMapper.selectByOwner(ownerId)) {
            result.add(MALL_DIRECTORY + "_" + shop.getId());
            result.add(SERVICE_DIRECTORY + "_" + shop.getId());
        }
        return result;
    }

    private List<String> categoryDirectories(String category) {
        return LOGO_DIRECTORY.equals(category) || MALL_DIRECTORY.equals(category)
                || SERVICE_DIRECTORY.equals(category)
                ? MERCHANT_UPLOAD_DIRECTORIES : USER_UPLOAD_DIRECTORIES;
    }

    private long calculateUsedBytes(List<String> categories, List<String> prefixes) {
        Path uploadRoot = Path.of(uploadProperties.getRoot()).toAbsolutePath().normalize();
        long total = 0L;
        for (String category : categories) {
            Path directory = uploadRoot.resolve(category);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> files = Files.list(directory)) {
                // 只累加本前缀的文件，同一目录下其他商家/用户的上传不会被算进自己的额度。
                total += files.filter(Files::isRegularFile)
                        .filter(path -> matchesPrefix(path.getFileName().toString(), prefixes))
                        .mapToLong(this::fileSize)
                        .sum();
            } catch (IOException exception) {
                log.error("统计上传空间失败，目录：{}", directory, exception);
                // 统计不出口径就直接拒绝本次上传：放行等于给出一个无法校验的额度。
                throw new BusinessException(500, "统计上传空间失败");
            }
        }
        return total;
    }

    private boolean matchesPrefix(String filename, List<String> prefixes) {
        for (String prefix : prefixes) {
            // 前缀必须连同分隔下划线一起匹配，否则 userId=1 会把 userId=12 的文件算进自己的额度。
            if (filename.startsWith(prefix + "_")) {
                return true;
            }
        }
        return false;
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            // 读不到大小按 0 计：一个被外部删掉的坏文件不该让整次上传失败，代价只是额度略微放宽。
            log.warn("读取上传文件大小失败：{}", path, exception);
            return 0L;
        }
    }

    private void deleteImageQuietly(String imageUrl) {
        // 只清理站内 /uploads/ 前缀的地址：外链头像等第三方 URL 不能当成自己目录下的文件去删。
        if (imageUrl == null || !imageUrl.startsWith(UPLOAD_URL_PREFIX)) {
            return;
        }
        Path uploadRoot = Path.of(uploadProperties.getRoot()).toAbsolutePath().normalize();
        Path target = uploadRoot.resolve(imageUrl.substring(UPLOAD_URL_PREFIX.length())).normalize();
        deleteImageQuietly(target);
    }

    private void deleteImageQuietly(Path target) {
        Path uploadRoot = Path.of(uploadProperties.getRoot()).toAbsolutePath().normalize();
        // 删除前再确认目标在上传根目录内：补偿清理只能删自己写坏的文件，不能顺着 .. 删到目录之外。
        if (!target.startsWith(uploadRoot)) {
            log.warn("拒绝清理上传目录之外的文件：{}", target);
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            log.warn("清理上传文件失败：{}", target, exception);
        }
    }

}
