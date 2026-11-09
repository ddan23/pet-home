package com.hnist.controller;

import com.hnist.common.constant.AccountRole;

import com.hnist.annotation.CurrentUser;
import com.hnist.common.constant.ProductType;
import com.hnist.annotation.RequireRole;
import com.hnist.common.result.Result;
import com.hnist.dto.SaveProductRequest;
import com.hnist.dto.UpdateStatusRequest;
import com.hnist.common.result.PageResult;
import com.hnist.entity.Product;
import com.hnist.entity.Shop;
import com.hnist.entity.User;
import com.hnist.service.ProductService;
import com.hnist.service.ShopService;
import com.hnist.service.AuditLogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 商品与服务控制器。 */
@RestController
@RequestMapping({"/api", "/api/v1"})
public class ProductController {

    private final ProductService productService;
    private final ShopService shopService;
    private final AuditLogService auditLogService;

    public ProductController(ProductService productService, ShopService shopService,
                             AuditLogService auditLogService) {
        this.productService = productService;
        this.shopService = shopService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/products")
    public Result<PageResult<Product>> listProducts(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "categoryCode", required = false) String categoryCode,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "shopId", required = false) Integer shopId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "12") int pageSize) {
        return Result.ok(productService.getUserProducts(
                resolveCategoryFilter(categoryCode, category), keyword, shopId, page, pageSize));
    }

    @GetMapping("/services")
    public Result<PageResult<Product>> listServices(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "categoryCode", required = false) String categoryCode,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "shopId", required = false) Integer shopId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "9") int pageSize) {
        return Result.ok(productService.getUserServices(
                resolveCategoryFilter(categoryCode, category), keyword, shopId, page, pageSize));
    }

    /** 商品详情走公开口径：非公开状态一律 404，不暴露存在性。 */
    @GetMapping("/products/{id}")
    public Result<Product> getProduct(@PathVariable Integer id) {
        Product product = productService.getPublicProductById(
                id, ProductType.GOODS.getCode());
        if (product == null) {
            return Result.fail(404, "商品不存在");
        }
        return Result.ok(product);
    }

    /** 服务详情走公开口径：非公开状态一律 404，不暴露存在性。 */
    @GetMapping("/services/{id}")
    public Result<Product> getService(@PathVariable Integer id) {
        Product service = productService.getPublicProductById(
                id, ProductType.SERVICE.getCode());
        if (service == null) {
            return Result.fail(404, "服务不存在");
        }
        return Result.ok(service);
    }

    /** 店铺范围由登录态推导（本人的开放旗舰商城），不接收前端传 shopId。 */
    @GetMapping("/merchant/products")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<PageResult<Product>> listMerchantProducts(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "categoryCode", required = false) String categoryCode,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "stockMax", required = false) Integer stockMax,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
            @CurrentUser User user) {
        Shop shop = shopService.getOpenFlagship(user.getId());
        return Result.ok(productService.getMerchantProducts(
                shop.getId(), keyword, resolveCategoryFilter(categoryCode, category),
                status, stockMax, ProductType.GOODS.getCode(), page, pageSize));
    }

    /** POST/PUT 共用：request.id 为空即创建、否则更新，商品固定归属登录商家的旗舰商城。 */
    @RequestMapping(value = "/merchant/products", method = {RequestMethod.POST, RequestMethod.PUT})
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Product> saveProduct(@Valid @RequestBody SaveProductRequest request,
                                       @CurrentUser User user) {
        Shop shop = shopService.getOpenFlagship(user.getId());
        Product product = productService.saveProduct(
                shop.getId(), request, ProductType.GOODS.getCode());
        return Result.ok(request.getId() == null ? "发布成功" : "更新成功", product);
    }

    @PutMapping("/merchant/products/{id}/toggle")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Void> toggleProduct(
            @PathVariable Integer id,
            @CurrentUser User user) {
        Shop shop = shopService.getOpenFlagship(user.getId());
        productService.toggleStatus(id, shop.getId());
        return Result.ok();
    }

    @DeleteMapping("/merchant/products/{id}")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Void> deleteProduct(
            @PathVariable Integer id,
            @CurrentUser User user) {
        Shop shop = shopService.getOpenFlagship(user.getId());
        productService.deleteProduct(id, shop.getId());
        return Result.ok("删除成功", null);
    }

    /** outletId 为空时按登录商家维度聚合全部门店，非空则校验归属后按门店过滤。 */
    @GetMapping("/merchant/services")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<PageResult<Product>> listMerchantServices(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "categoryCode", required = false) String categoryCode,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "outletId", required = false) Integer outletId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @CurrentUser User user) {
        if (outletId != null) {
            Shop outlet = shopService.getOpenServiceOutlet(outletId, user.getId());
            return Result.ok(productService.getMerchantProducts(
                    outlet.getId(), keyword, resolveCategoryFilter(categoryCode, category),
                    status, null, ProductType.SERVICE.getCode(), page, pageSize));
        }
        return Result.ok(productService.getMerchantServicesByOwner(
                user.getId(), keyword, resolveCategoryFilter(categoryCode, category),
                status, page, pageSize));
    }

    /** 服务不存在或类型不符返回 404，随后再经归属校验，防止跨店读取他人服务。 */
    @GetMapping("/merchant/services/{id}")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Product> getMerchantService(@PathVariable Integer id, @CurrentUser User user) {
        Product product = productService.getProductById(id);
        if (product == null
                || !ProductType.SERVICE.getCode().equals(product.getProductType())) {
            return Result.fail(404, "服务不存在");
        }
        shopService.getOwnedServiceOutlet(product.getShopId(), user.getId());
        return Result.ok(product);
    }

    /** outletId 必填且必须是本人的服务门店，服务只能发布到归属门店。 */
    @RequestMapping(value = "/merchant/services", method = {RequestMethod.POST, RequestMethod.PUT})
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Product> saveService(@Valid @RequestBody SaveProductRequest request,
                                       @RequestParam(name = "outletId") Integer outletId,
                                       @CurrentUser User user) {
        request.setProductType(ProductType.SERVICE.getCode());
        Shop shop = shopService.getOpenServiceOutlet(outletId, user.getId());
        Product product = productService.saveProduct(
                shop.getId(), request, ProductType.SERVICE.getCode());
        return Result.ok(request.getId() == null ? "发布成功" : "更新成功", product);
    }

    @PutMapping("/merchant/services/{id}/toggle")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Void> toggleService(
            @PathVariable Integer id,
            @RequestParam(name = "outletId") Integer outletId,
            @CurrentUser User user) {
        Shop shop = shopService.getOpenServiceOutlet(outletId, user.getId());
        productService.toggleStatus(id, shop.getId());
        return Result.ok();
    }

    @DeleteMapping("/merchant/services/{id}")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Void> deleteService(
            @PathVariable Integer id,
            @RequestParam(name = "outletId") Integer outletId,
            @CurrentUser User user) {
        Shop shop = shopService.getOpenServiceOutlet(outletId, user.getId());
        productService.deleteProduct(id, shop.getId());
        return Result.ok("删除成功", null);
    }

    @GetMapping("/admin/products")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<PageResult<Product>> listAdminProducts(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "productType", required = false) String productType,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return Result.ok(productService.getAdminProducts(keyword, status, productType, page, pageSize));
    }

    @PutMapping("/admin/products/{id}/status")
    @RequireRole(AccountRole.ADMIN_CODE)
    public Result<Void> updateProductStatus(@PathVariable Integer id,
                                            @Valid @RequestBody UpdateStatusRequest request,
                                            @CurrentUser User operator) {
        productService.updateProductStatus(id, request.getStatus());
        auditLogService.record(operator.getId(), "PRODUCT_STATUS", "PRODUCT", id.longValue(),
                java.util.Map.of("status", request.getStatus()));
        return Result.ok();
    }

    private String resolveCategoryFilter(String categoryCode, String categoryName) {
        return categoryCode == null || categoryCode.isBlank()
                ? categoryName : categoryCode;
    }
}
