package com.hnist.service.impl;

import com.hnist.common.exception.BusinessException;
import com.hnist.common.constant.ProductStatus;
import com.hnist.common.constant.ProductType;
import com.hnist.mapper.AppointmentScheduleMapper;
import com.hnist.mapper.ProductMapper;
import com.hnist.dto.SaveProductRequest;
import com.hnist.common.result.PageResult;
import com.hnist.entity.Product;
import com.hnist.entity.ProductCategory;
import com.hnist.service.CategoryService;
import com.hnist.service.ProductService;
import com.hnist.common.util.PageUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 商品与服务的发布、编辑、上下架及前台/商家端查询。 */
@Service
public class ProductServiceImpl implements ProductService {

    private static final String TARGET_PET_ALL = "ALL";
    private static final String LIFE_STAGE_ALL = "ALL";
    private static final int HEALTH_TAGS_MAX_LENGTH = 500;
    private static final Set<String> TARGET_PET_VALUES =
            Set.of("CAT", "DOG", TARGET_PET_ALL);
    private static final Set<String> LIFE_STAGE_VALUES =
            Set.of("YOUNG", "ADULT", "SENIOR", LIFE_STAGE_ALL);

    private final ProductMapper productMapper;
    private final CategoryService categoryService;
    private final AppointmentScheduleMapper appointmentScheduleMapper;

    public ProductServiceImpl(ProductMapper productMapper,
                              CategoryService categoryService,
                              AppointmentScheduleMapper appointmentScheduleMapper) {
        this.productMapper = productMapper;
        this.categoryService = categoryService;
        this.appointmentScheduleMapper = appointmentScheduleMapper;
    }

    @Override
    public PageResult<Product> getUserProducts(String categoryCode, String keyword,
                                               Integer shopId, int page, int pageSize) {
        // 分页参数先夹紧：page 最小为 1、pageSize 上限 100，防止 offset 为负或单次拉取过多行。
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(pageSize);
        int offset = (safePage - 1) * safePageSize;
        // 用户端可见范围由 selectForUser 的 SQL 决定：p.status='on'、product_type='goods'，且店铺 open、商家账号 normal。
        List<Product> list = productMapper.selectForUser(
                categoryCode, keyword, shopId, offset, safePageSize);
        // 总数用独立的 count 语句统计，不带 LIMIT，否则分页器只能拿到当前页条数。
        int total = productMapper.countForUser(categoryCode, keyword, shopId);
        return new PageResult<>(list, total, safePage, safePageSize);
    }

    @Override
    public PageResult<Product> getUserServices(String categoryCode, String keyword,
                                               Integer shopId, int page, int pageSize) {
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(pageSize);
        int offset = (safePage - 1) * safePageSize;
        // 服务列表不能复用商品列表：selectServicesForUser 固定 product_type='service'，服务不占库存、靠排班预约承接。
        List<Product> list = productMapper.selectServicesForUser(
                categoryCode, keyword, shopId, offset, safePageSize);
        int total = productMapper.countServicesForUser(categoryCode, keyword, shopId);
        return new PageResult<>(list, total, safePage, safePageSize);
    }

    @Override
    public Product getProductById(Integer id) {
        // selectById 不带 status/product_type 条件，只供商家端与后台读取真实状态，对外详情必须走 getPublicProductById。
        return productMapper.selectById(id);
    }

    @Override
    // 公开详情进缓存：key 含类型，goods 与 service 同 id 互不串用；未上架商品的 null 结果同样缓存，防止穿透打库。
    @Cacheable(cacheNames = "productDetail", key = "#id + ':' + #productType")
    public Product getPublicProductById(Integer id, String productType) {
        // 对外详情的可售范围下沉到 SQL：p.status='on' 且 product_type 匹配且店铺营业，返回 null 由 Controller 统一转 404，
        // 不区分「不存在」与「不可售」，否则可用 id 逐个探测未上架商品。
        return productMapper.selectPublicById(id, productType);
    }

    @Override
    public PageResult<Product> getMerchantProducts(Integer shopId, String keyword,
                                                   String categoryCode, String status,
                                                   Integer stockMax, String productType,
                                                   int page,
                                                   int pageSize) {
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(pageSize);
        int offset = (safePage - 1) * safePageSize;
        // stockMax 是补货提醒用的反向筛选（SQL 取 stock <= stockMax 并按库存升序），传入负数会让条件恒不成立、列表恒空，先归零。
        Integer safeStockMax = stockMax == null ? null : Math.max(stockMax, 0);
        List<Product> list = productMapper.selectByShopId(
                shopId, keyword, categoryCode, status, safeStockMax, productType,
                offset, safePageSize);
        int total = productMapper.countByShopId(
                shopId, keyword, categoryCode, status, safeStockMax, productType);
        return new PageResult<>(list, total, safePage, safePageSize);
    }

    @Override
    public PageResult<Product> getMerchantServicesByOwner(Integer ownerId, String keyword,
                                                           String categoryCode, String status,
                                                           int page, int pageSize) {
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(pageSize);
        int offset = (safePage - 1) * safePageSize;
        // 总部视角按 ownerId 跨门店查询，不限定单个 shopId；SQL 内仍要求店铺 open 且 product_type='service'。
        List<Product> list = productMapper.selectServicesByOwner(
                ownerId, keyword, categoryCode, status, offset, safePageSize);
        int total = productMapper.countServicesByOwner(
                ownerId, keyword, categoryCode, status);
        return new PageResult<>(list, total, safePage, safePageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // 新增/改价/改库存直接影响公开详情与可售性：详情缓存条目量等于商品总数，全量逐出比精确 key 更不易漏。
    @CacheEvict(cacheNames = "productDetail", allEntries = true)
    public Product saveProduct(Integer shopId, SaveProductRequest request, String defaultProductType) {
        // 更新场景先按 id 读出原记录再比对 shop_id：店铺归属只能来自登录态，请求体无法把商品改挂到别家门店。
        Product product = request.getId() == null ? new Product() : productMapper.selectById(request.getId());
        // 不存在与非本人店铺统一返回 403「无权操作该商品」，避免用 id 顺序探测他人商品。
        if (product == null || (request.getId() != null && !shopId.equals(product.getShopId()))) {
            throw new BusinessException(403, "无权操作该商品");
        }
        // 商品与服务的必填项不同：goods 必须有库存，service 必须有结构化服务时长，按 productType 分流校验。
        validateGoodsRequiredFields(request, defaultProductType);
        validateServiceRequiredFields(request, defaultProductType);
        // 分类只允许启用且类型匹配的二级分类：列表与分类树都靠 category_id 关联，挂错层级会让人工统计和筛选同时失真。
        ProductCategory category = categoryService.requireAssignableCategory(
                request.getCategoryId(), defaultProductType);
        product.setShopId(shopId);
        product.setProductType(defaultProductType);
        product.setCategoryId(category.getId());
        product.setCategoryCode(category.getCategoryCode());
        product.setCategoryName(category.getCategoryName());
        product.setCategory(category.getCategoryName());
        product.setName(request.getName().trim());
        product.setDescription(trimToNull(request.getDescription()));
        product.setTargetPet(normalizeOption(
                request.getTargetPet(), TARGET_PET_VALUES,
                TARGET_PET_ALL, "适用宠物"));
        product.setLifeStage(normalizeOption(
                request.getLifeStage(), LIFE_STAGE_VALUES,
                LIFE_STAGE_ALL, "适用阶段"));
        product.setHealthTags(normalizeHealthTags(request.getHealthTags()));
        // 价格原样透传 BigDecimal，小数位由 price decimal(10,2) 收口，不经 double 转换以免出现精度漂移。
        product.setPrice(request.getPrice());
        // 库存缺失时落 0：stock 列允许 NULL，带着 null 上架会让扣减判断和「库存为 0 自动下架」全部失效。
        product.setStock(request.getStock() == null ? 0 : request.getStock());
        // 服务时长只认结构化分钟数：排班时段按 durationMinutes + 模板间隔切分，
        // duration 文本由分钟数同步生成，保证用户端展示与服务实际占用一致；服务间隔归排班模板管理。
        Integer durationMinutes = request.getDurationMinutes();
        if (ProductType.SERVICE.getCode().equals(defaultProductType)) {
            product.setDuration(durationMinutes + " 分钟");
        }
        product.setDurationMinutes(durationMinutes);
        product.setImageUrl(request.getImageUrl());
        // 实物商品库存为 0 不允许保持上架：新建时直接定初始状态；更新分支必须补一次 updateStatus，因为 update 语句不写 status 列。
        boolean outOfStock = ProductType.GOODS.getCode().equals(product.getProductType())
                && (product.getStock() == null || product.getStock() <= 0);
        if (product.getId() == null) {
            product.setStatus(outOfStock ? ProductStatus.OFF.getCode() : ProductStatus.ON.getCode());
            productMapper.insert(product);
        } else {
            productMapper.update(product);
            if (outOfStock) {
                productMapper.updateStatus(product.getId(), ProductStatus.OFF.getCode());
            }
        }
        return productMapper.selectById(product.getId());
    }

    private void validateGoodsRequiredFields(SaveProductRequest request, String productType) {        // 服务类整体跳过库存校验：service 没有实物库存，强制填 stock 会让服务无法发布。
        if (!ProductType.GOODS.getCode().equals(productType)) {
            return;
        }
        if (request.getStock() == null) {
            throw new BusinessException("请填写库存数量");
        }
        // 库存不能为负：stock 列没有 CHECK 约束，负数会同时让库存筛选、下单校验和扣减条件失真，必须在写库前拦住。
        if (request.getStock() < 0) {
            throw new BusinessException("库存不能小于0");
        }
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new BusinessException("请填写商品描述");
        }
        if (request.getTargetPet() == null || request.getTargetPet().isBlank()) {
            throw new BusinessException("请选择适用宠物");
        }
        if (request.getLifeStage() == null || request.getLifeStage().isBlank()) {
            throw new BusinessException("请选择适用阶段");
        }
    }

    /**
     * 服务类必填项校验：结构化时长是排班时段切分的唯一依据，缺失会让时段生成无从切分；
     * 分钟数范围由 DTO 注解先行拦截，这里只补 service 类型的必填约束（goods 无需时长）。
     */
    private void validateServiceRequiredFields(SaveProductRequest request, String productType) {
        if (!ProductType.SERVICE.getCode().equals(productType)) {
            return;
        }
        if (request.getDurationMinutes() == null) {
            throw new BusinessException("请填写服务时长");
        }
    }

    @Override
    // 上下架改变公开可见性，缓存里的详情必须立即失效
    @CacheEvict(cacheNames = "productDetail", allEntries = true)
    public void toggleStatus(Integer productId, Integer shopId) {
        // 上下架是商家操作，归属先校验：商品不存在或不属于当前 shopId 一律 404，不暴露他人商品是否在售。
        Product p = productMapper.selectById(productId);
        if (p == null || !p.getShopId().equals(shopId)) {
            throw new BusinessException(404, "商品不存在");
        }
        String newStatus = ProductStatus.ON.getCode().equals(p.getStatus())
                ? ProductStatus.OFF.getCode() : ProductStatus.ON.getCode();
        requireStockForOnShelf(p, newStatus);
        productMapper.updateStatus(productId, newStatus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // 删除后详情必须不可再访问，逐出该商品的缓存
    @CacheEvict(cacheNames = "productDetail", allEntries = true)
    public void deleteProduct(Integer productId, Integer shopId) {
        // 物理删除并先校验归属：cart_items.fk_cart_product 是 ON DELETE CASCADE，用户购物车里的残留行由数据库一并清理，无需业务补偿。
        Product p = productMapper.selectById(productId);
        if (p == null || !p.getShopId().equals(shopId)) {
            throw new BusinessException(404, "商品不存在");
        }
        // 时段与预约引用两张表对 products 的外键没有级联，直接删会外键报错变 500；
        // 被预约的时段是履约凭证不能静默清掉，必须在删除前给出带出路的提示。
        if (productMapper.existsOrderRefByServiceId(productId)) {
            throw new BusinessException("该服务存在历史预约记录，请改用下架，不能物理删除");
        }
        if (productMapper.existsSlotByServiceId(productId)) {
            // 模板生成的时段在没有订单引用时随服务一并清理（无预约引用的孤儿时段）。
            productMapper.deleteSlotsByServiceId(productId);
        }
        // 排班模板只是商家配置，随服务一并删除，避免残留孤儿模板。
        appointmentScheduleMapper.deleteByServiceId(productId);
        productMapper.deleteById(productId);
    }

    @Override
    public PageResult<Product> getAdminProducts(String keyword, String status,
                                                String productType, int page, int pageSize) {
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(pageSize);
        int offset = (safePage - 1) * safePageSize;
        // 管理员视角不做店铺隔离也不默认过滤状态，status/productType 由查询参数按需收窄，便于全平台排查。
        List<Product> list = productMapper.selectAllForAdmin(
                keyword, status, productType, offset, safePageSize);
        int total = productMapper.countAllForAdmin(keyword, status, productType);
        return new PageResult<>(list, total, safePage, safePageSize);
    }

    @Override
    // 管理员强制上下架同样改变公开可见性，逐出详情缓存
    @CacheEvict(cacheNames = "productDetail", allEntries = true)
    public void updateProductStatus(Integer productId, String status) {
        // 只接受 on/off：products.status 是 enum，写入其他值会抛 SQL 异常，这里提前转成可读的业务异常。
        if (!ProductStatus.ON.getCode().equals(status)
                && !ProductStatus.OFF.getCode().equals(status)) {
            throw new BusinessException("商品状态不合法");
        }
        Product p = productMapper.selectById(productId);
        if (p == null) {
            throw new BusinessException(404, "商品不存在");
        }
        requireStockForOnShelf(p, status);
        productMapper.updateStatus(productId, status);
    }

    private void requireStockForOnShelf(Product product, String targetStatus) {
        // 只有 goods 上架才要求正库存：service 靠排班承接，库存字段对它没有意义；商家端与管理端共用同一条门槛。
        if (ProductStatus.ON.getCode().equals(targetStatus)
                && ProductType.GOODS.getCode().equals(product.getProductType())
                && (product.getStock() == null || product.getStock() <= 0)) {
            throw new BusinessException("商品库存为 0，请先补货再上架");
        }
    }

    private String normalizeOption(String value, Set<String> allowedValues,
                                   String defaultValue, String fieldName) {
        // 枚举型字段走白名单归一：target_pet、life_stage 是列表筛选维度，空值统一落 ALL，写入集合外的值会让该商品永远筛不出来。
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new BusinessException(fieldName + "取值不合法");
        }
        return normalized;
    }

    private String normalizeHealthTags(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        Set<String> uniqueTags = new LinkedHashSet<>();
        // 商家可混用中英文标点，先按多种分隔符切分再用 LinkedHashSet 去重保序，否则同一标签会重复出现。
        for (String item : value.split("[\\s,，;；、]+")) {
            String tag = item.trim();
            if (!tag.isEmpty()) {
                uniqueTags.add(tag);
            }
        }
        String normalized = String.join(",", uniqueTags);
        // 上限与 health_tags varchar(500) 列宽对齐：超长会触发数据库截断或报错，提前转成业务异常让商家自己精简。
        if (normalized.length() > HEALTH_TAGS_MAX_LENGTH) {
            throw new BusinessException("健康标签不能超过500个字符");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null ? null : value.trim();
    }

}
