package com.hnist.support;

import com.hnist.common.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/** 图片上传校验器：扩展名白名单 + Content-Type + 文件头魔数三重比对，防止改后缀伪装。 */
public final class ImageFileValidator {

    /** 单文件大小上限：5MB。 */
    private static final long MAX_FILE_BYTES = 5L * 1024L * 1024L;
    /** 读入的文件头字节数，需覆盖 WebP 魔数（RIFF....WEBP）的判定范围。 */
    private static final int HEADER_LENGTH = 12;
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

    private ImageFileValidator() {
    }

    /**
     * 依次校验：文件非空 → ≤5MB → Content-Type 为 image/* → 扩展名在白名单 → 文件头魔数与扩展名一致。
     * 任一环节失败抛 {@link BusinessException}（用户可见错误）；通过时返回标准化扩展名供落盘命名。
     */
    public static String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择图片文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BusinessException(413, "上传文件不能超过5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null
                || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException("只能上传图片文件");
        }
        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("仅支持 JPG、PNG、GIF 或 WebP 图片");
        }
        String detectedExtension = detectExtension(readHeader(file));
        if (detectedExtension == null || !matchesExtension(extension, detectedExtension)) {
            throw new BusinessException("图片内容与文件格式不一致");
        }
        return extension;
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return inputStream.readNBytes(HEADER_LENGTH);
        } catch (IOException exception) {
            throw new BusinessException(500, "读取图片文件失败");
        }
    }

    private static String detectExtension(byte[] header) {
        if (startsWith(header, 0, 0xFF, 0xD8, 0xFF)) {
            return ".jpg";
        }
        if (startsWith(header, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return ".png";
        }
        if (startsWith(header, 0, 0x47, 0x49, 0x46, 0x38, 0x37, 0x61)
                || startsWith(header, 0, 0x47, 0x49, 0x46, 0x38, 0x39, 0x61)) {
            return ".gif";
        }
        if (header.length >= HEADER_LENGTH
                && startsWith(header, 0, 0x52, 0x49, 0x46, 0x46)
                && startsWith(header, 8, 0x57, 0x45, 0x42, 0x50)) {
            return ".webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] value, int offset, int... expected) {
        if (value.length < offset + expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (Byte.toUnsignedInt(value[offset + index]) != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesExtension(String extension, String detectedExtension) {
        if (".jpeg".equals(extension)) {
            return ".jpg".equals(detectedExtension);
        }
        return extension.equals(detectedExtension);
    }

    private static String getExtension(String originalName) {
        if (originalName == null) {
            return "";
        }
        int index = originalName.lastIndexOf('.');
        return index > 0 ? originalName.substring(index).toLowerCase(Locale.ROOT) : "";
    }
}
