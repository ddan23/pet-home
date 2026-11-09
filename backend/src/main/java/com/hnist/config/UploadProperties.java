package com.hnist.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 绑定 app.upload.*：图片落盘根目录与按角色的磁盘配额，配额在保存前校验，超限拒绝写入。 */
@Data
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /** 上传根目录（APP_UPLOAD_ROOT，默认 ./uploads）；WebMvcConfig 把它映射到 /uploads/** 对外可访问。 */
    private String root = "./uploads";

    /** 单用户图片总配额，单位字节（UPLOAD_USER_QUOTA_BYTES，默认 52428800 即 50MB），覆盖头像/帖子/宠物等用户侧图片。 */
    private long userQuotaBytes = 50L * 1024L * 1024L;

    /** 单商家图片总配额，单位字节（UPLOAD_MERCHANT_QUOTA_BYTES，默认 209715200 即 200MB），覆盖商城/服务/Logo 图片。 */
    private long merchantQuotaBytes = 200L * 1024L * 1024L;
}
