package com.hnist.service.impl;

import com.hnist.service.AiImageContentService;

import com.hnist.config.UploadProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/** 把本地社区图片读成模型可用的 data URL，并给出内容哈希。 */
@Service
public class AiImageContentServiceImpl implements AiImageContentService {

    // 上限 5MB：base64 会再膨胀约三分之一，更大的图会让单次审核请求体过大，拖长耗时甚至被上游拒绝。
    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
    private static final String UPLOAD_PREFIX = "/uploads/";

    private final UploadProperties uploadProperties;

    public AiImageContentServiceImpl(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    @Override
    public ImageContent load(String imageUrl) {
        // 只认本站上传目录：外部地址（含带签名参数的 CDN 链接）一律不读，避免被当成任意 URL 抓取入口（SSRF）。
        if (imageUrl == null || imageUrl.isBlank() || !imageUrl.startsWith(UPLOAD_PREFIX)) {
            return null;
        }
        Path uploadRoot = Path.of(uploadProperties.getRoot()).toAbsolutePath().normalize();
        Path target = uploadRoot.resolve(imageUrl.substring(UPLOAD_PREFIX.length())).normalize();
        // normalize 后再比对前缀，拦住 ../ 穿越到上传目录之外；越界、非普通文件、路径不存在统一返回 null 交给审核链路转人工。
        if (!target.startsWith(uploadRoot) || !Files.isRegularFile(target)) {
            return null;
        }
        try {
            long size = Files.size(target);
            if (size <= 0 || size > MAX_IMAGE_BYTES) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(target);
            String mimeType = detectMimeType(target);
            String dataUrl = "data:" + mimeType + ";base64,"
                    + Base64.getEncoder().encodeToString(bytes);
            // 哈希与 dataUrl 一起返回：审核日志要能追溯到具体这张图，而 dataUrl 只用于本次模型调用、不落库。
            return new ImageContent(dataUrl, sha256(bytes));
        } catch (IOException exception) {
            // 读取失败不向上抛：load 的契约是“读不到返回 null”，由 ContentModerationServiceImpl 统一判为图片待人工复核。
            return null;
        }
    }

    private String detectMimeType(Path path) {
        // 按扩展名推断 MIME 只影响 data URL 头，未知后缀按 image/jpeg 兜底，不承担安全校验职责。
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".png")) {
            return "image/png";
        }
        if (filename.endsWith(".gif")) {
            return "image/gif";
        }
        if (filename.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 是 JDK 必带算法，缺失只可能是运行环境被裁剪：这是部署错误，直接抛出而不是返回空哈希写入审核日志。
            throw new IllegalStateException("当前JVM不支持SHA-256", exception);
        }
    }

}
