package com.hnist.controller;

import com.hnist.common.constant.AccountRole;

import com.hnist.annotation.CurrentUser;
import com.hnist.annotation.RequireRole;
import com.hnist.common.constant.ShopStatus;
import com.hnist.common.result.Result;
import com.hnist.vo.MerchantOrganizationResponse;
import com.hnist.vo.ShopListResponse;
import com.hnist.dto.UpdateMerchantShopRequest;
import com.hnist.dto.UpdateStatusRequest;
import com.hnist.common.result.PageResult;
import com.hnist.entity.Shop;
import com.hnist.entity.User;
import com.hnist.service.ShopService;
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

import java.util.List;
import java.util.Map;

/** 店铺管理控制器。 */
@RestController
@RequestMapping({"/api", "/api/v1"})
public class ShopController {

    private final ShopService shopService;
    private final AuditLogService auditLogService;

    public ShopController(ShopService shopService, AuditLogService auditLogService) {
        this.shopService = shopService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/merchant/shops")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<ShopListResponse> listOwnedShops(@CurrentUser User user) {
        List<Shop> shops = shopService.listOwnedShops(user.getId());
        return Result.ok(new ShopListResponse(shops, shops.size()));
    }

    @GetMapping("/merchant/organization")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<MerchantOrganizationResponse> getOrganization(@CurrentUser User user) {
        return Result.ok(shopService.getOrganization(user.getId()));
    }

    /** 新建旗舰店提交后进入待审核状态，由管理员在审核端开通。 */
    @PostMapping("/merchant/flagship")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Shop> createFlagship(@Valid @RequestBody UpdateMerchantShopRequest request,
                                       @CurrentUser User user) {
        return Result.ok("旗舰店已提交审核",
                shopService.createFlagship(user.getId(), request));
    }

    @PutMapping("/merchant/flagship")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Shop> updateFlagship(@Valid @RequestBody UpdateMerchantShopRequest request,
                                       @CurrentUser User user) {
        return Result.ok("旗舰店资料已更新",
                shopService.updateFlagship(user.getId(), request));
    }

    @PutMapping("/merchant/flagship/status")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Void> updateFlagshipStatus(@Valid @RequestBody UpdateStatusRequest request,
                                             @CurrentUser User user) {
        shopService.updateFlagshipStatus(user.getId(), request.getStatus());
        return Result.ok("商城店铺状态已更新", null);
    }

    @PostMapping("/merchant/service-outlets")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Shop> createServiceOutlet(@Valid @RequestBody UpdateMerchantShopRequest request,
                                            @CurrentUser User user) {
        return Result.ok("服务门店已提交审核",
                shopService.createServiceOutlet(user.getId(), request));
    }

    @PutMapping("/merchant/service-outlets/{id}")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Shop> updateServiceOutlet(@PathVariable Integer id,
                                            @Valid @RequestBody UpdateMerchantShopRequest request,
                                            @CurrentUser User user) {
        return Result.ok("服务门店资料已更新",
                shopService.updateServiceOutlet(user.getId(), id, request));
    }

    @PutMapping("/merchant/service-outlets/{id}/status")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Void> updateServiceOutletStatus(@PathVariable Integer id,
                                                  @Valid @RequestBody UpdateStatusRequest request,
                                                  @CurrentUser User user) {
        shopService.updateOwnedServiceOutletStatus(user.getId(), id, request.getStatus());
        return Result.ok("服务门店状态已更新", null);
    }

    @GetMapping("/merchant/shops/{id}")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Shop> getOwnedShop(@PathVariable Integer id, @CurrentUser User user) {
        return Result.ok(shopService.getOwnedShop(id, user.getId()));
    }

    @PostMapping("/merchant/shops")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Void> createShop(@Valid @RequestBody UpdateMerchantShopRequest request,
                                   @CurrentUser User user) {
        shopService.createShop(user.getId(), request);
        return Result.ok("创建成功，等待审核", null);
    }

    @PutMapping("/merchant/shops")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Shop> updateShop(@Valid @RequestBody UpdateMerchantShopRequest request,
                                   @CurrentUser User user) {
        return Result.ok("修改成功", shopService.updateShop(user.getId(), request));
    }

    @GetMapping("/admin/shops")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<PageResult<Shop>> listShops(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "merchantId", required = false) Integer merchantId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.ok(shopService.listShops(status, type, merchantId, keyword, page, pageSize));
    }

    @GetMapping("/admin/shops/{id}")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<Shop> getShopDetail(@PathVariable Integer id) {
        return Result.ok(shopService.getShopDetail(id));
    }

    /** status 缺省时只查 pending_audit 状态的店铺。 */
    @GetMapping("/admin/audits")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<PageResult<Shop>> listAudits(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        String filterStatus = status == null
                ? ShopStatus.PENDING_AUDIT.getCode() : status;
        return Result.ok(shopService.listShops(filterStatus, null, null, keyword, page, pageSize));
    }

    @PutMapping("/admin/audits/{id}")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<Void> auditShop(@PathVariable Integer id,
                                  @Valid @RequestBody UpdateStatusRequest request,
                                  @CurrentUser User operator) {
        shopService.auditShop(id, request.getStatus());
        auditLogService.record(operator.getId(), "SHOP_AUDIT", "SHOP", id.longValue(),
                Map.of("status", request.getStatus()));
        return Result.ok("操作成功", null);
    }

    @PutMapping("/admin/shops/{id}/status")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<Void> updateShopStatus(@PathVariable Integer id,
                                         @Valid @RequestBody UpdateStatusRequest request,
                                         @CurrentUser User operator) {
        shopService.updateShopStatus(id, request.getStatus());
        auditLogService.record(operator.getId(), "SHOP_STATUS", "SHOP", id.longValue(),
                Map.of("status", request.getStatus()));
        return Result.ok();
    }
}
