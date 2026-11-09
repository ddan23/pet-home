package com.hnist.controller;

import com.hnist.common.constant.AccountRole;

import com.hnist.annotation.CurrentUser;
import com.hnist.annotation.RequireLogin;
import com.hnist.annotation.RequireRole;
import com.hnist.common.result.Result;
import com.hnist.entity.Shop;
import com.hnist.entity.User;
import com.hnist.service.ShopService;
import com.hnist.service.UploadService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 图片上传控制器。 */
@RestController
@RequestMapping({"/api", "/api/v1"})
@RequireLogin
public class UploadController {

    private final UploadService uploadService;
    private final ShopService shopService;

    public UploadController(UploadService uploadService, ShopService shopService) {
        this.uploadService = uploadService;
        this.shopService = shopService;
    }

    @PostMapping("/user/avatar")
    @RequireRole(AccountRole.USER_CODE)
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file,
                                       @CurrentUser User user) {
        return Result.ok(uploadService.uploadAvatar(user.getId(), file));
    }

    @PostMapping("/post/image")
    @RequireRole(AccountRole.USER_CODE)
    public Result<String> uploadPostImage(@RequestParam("file") MultipartFile file,
                                          @CurrentUser User user) {
        return Result.ok(uploadService.uploadPostImage(user.getId(), file));
    }

    /** 图片归属登录商家的旗舰商城，shopId 由登录态推导而非前端传入。 */
    @PostMapping("/product/image")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<String> uploadProductImage(@RequestParam("file") MultipartFile file,
                                             @CurrentUser User user) {
        Shop shop = shopService.getOpenFlagship(user.getId());
        return Result.ok(uploadService.uploadProductImage(user.getId(), shop.getId(), file));
    }

    /** outletId 必填且必须是本人的服务门店，图片归属校验通过后的门店。 */
    @PostMapping("/service/image")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<String> uploadServiceImage(@RequestParam("file") MultipartFile file,
                                             @RequestParam(name = "outletId") Integer outletId,
                                             @CurrentUser User user) {
        Shop shop = shopService.getOpenServiceOutlet(outletId, user.getId());
        return Result.ok(uploadService.uploadServiceImage(user.getId(), shop.getId(), file));
    }

    @PostMapping("/logo/image")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<String> uploadLogo(@RequestParam("file") MultipartFile file,
                                     @CurrentUser User user) {
        return Result.ok(uploadService.uploadLogo(user.getId(), file));
    }

    @PostMapping("/pet/image")
    @RequireRole(AccountRole.USER_CODE)
    public Result<String> uploadPetImage(@RequestParam("file") MultipartFile file,
                                         @CurrentUser User user) {
        return Result.ok(uploadService.uploadPetImage(user.getId(), file));
    }
}
