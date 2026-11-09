package com.hnist.service;

import org.springframework.web.multipart.MultipartFile;

/** 图片上传服务。 */
public interface UploadService {

    // 返回 /uploads/ 开头的相对 URL；文件先落盘再写 users.avatar，写库失败会删掉刚上传的文件。
    String uploadAvatar(Integer userId, MultipartFile file);

    // 帖子图只返回 URL，不落任何业务表，由发帖接口自行保存引用。
    String uploadPostImage(Integer userId, MultipartFile file);

    // shopId 必须由调用方经登录态校验后传入（getOpenFlagship），服务层只按它做额度归属，不再校验归属。
    String uploadProductImage(Integer ownerId, Integer shopId, MultipartFile file);

    // 门店服务图与商品图共用商家额度，按 logo_/mall_/service_ 前缀分别统计。
    String uploadServiceImage(Integer ownerId, Integer shopId, MultipartFile file);

    // 商家 logo 计入商家额度（logo_ 前缀），与个人额度互不挤占。
    String uploadLogo(Integer userId, MultipartFile file);

    // 宠物图计入个人额度（pet_ 前缀）。
    String uploadPetImage(Integer userId, MultipartFile file);
}
