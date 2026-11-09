package com.hnist.service.impl;

import com.hnist.common.exception.BusinessException;
import com.hnist.common.constant.AccountStatus;
import com.hnist.common.constant.ShopStatus;
import com.hnist.common.constant.ShopType;
import com.hnist.vo.MerchantOrganizationResponse;
import com.hnist.mapper.ShopMapper;
import com.hnist.mapper.UserMapper;
import com.hnist.dto.UpdateMerchantShopRequest;
import com.hnist.common.result.PageResult;
import com.hnist.entity.Shop;
import com.hnist.service.ShopService;
import com.hnist.common.util.PageUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** 商家店铺组织的归属校验、创建审核和营业状态流转实现。 */
@Service
public class ShopServiceImpl implements ShopService {

    private final ShopMapper shopMapper;
    private final UserMapper userMapper;

    public ShopServiceImpl(ShopMapper shopMapper, UserMapper userMapper) {
        this.shopMapper = shopMapper;
        this.userMapper = userMapper;
    }

    @Override
    public List<Shop> listOwnedShops(Integer ownerId) {
        return shopMapper.selectByOwner(ownerId);
    }

    @Override
    public MerchantOrganizationResponse getOrganization(Integer ownerId) {
        // 旗舰店允许为空：组织页要在商家还没建店时正常渲染，所以直接取 mapper 结果而不经过 getFlagship 的 403 检查。
        Shop flagship = shopMapper.selectFlagshipByOwner(ownerId);
        List<Shop> serviceOutlets = shopMapper.selectServiceOutletsByOwner(ownerId);
        return new MerchantOrganizationResponse(flagship, serviceOutlets, serviceOutlets.size());
    }

    @Override
    public Shop getOwnedShop(Integer shopId, Integer ownerId) {
        // 归属只能看 ownerId，不能信路径参数：selectById 不带 owner 条件，因此这里同时挡掉越权和已归档店铺，一律按 404 回答。
        Shop shop = shopMapper.selectById(shopId);
        // 店铺不做物理删除：orders、products 的外键是 ON DELETE CASCADE，删店会连带清掉历史订单与商品；商家侧读取都带 archived_at IS NULL，归档店铺等同不存在。
        if (shop == null || shop.getArchivedAt() != null
                || !ownerId.equals(shop.getOwnerId())) {
            throw new BusinessException(404, "店铺不存在");
        }
        return shop;
    }

    @Override
    public Shop getFlagship(Integer ownerId) {
        // 用 403 而非 404：商家没有旗舰店属于可修复的前置条件，文案直接引导创建，前端据此跳转开通流程。
        Shop shop = shopMapper.selectFlagshipByOwner(ownerId);
        if (shop == null) {
            throw new BusinessException(403, "请先创建旗舰店");
        }
        return shop;
    }

    @Override
    public Shop getOpenFlagship(Integer ownerId) {
        Shop shop = getFlagship(ownerId);
        requireOpen(shop);
        return shop;
    }

    @Override
    public List<Shop> listServiceOutlets(Integer ownerId) {
        return shopMapper.selectServiceOutletsByOwner(ownerId);
    }

    @Override
    public Shop getOwnedServiceOutlet(Integer outletId, Integer ownerId) {
        // 归属、类型、归档三个条件都已下推到 selectOwnedServiceOutlet，查到 null 就说明不是本商家的服务门店。
        Shop shop = shopMapper.selectOwnedServiceOutlet(outletId, ownerId);
        if (shop == null) {
            throw new BusinessException(404, "服务门店不存在");
        }
        return shop;
    }

    @Override
    public Shop getOpenServiceOutlet(Integer outletId, Integer ownerId) {
        Shop shop = getOwnedServiceOutlet(outletId, ownerId);
        requireOpen(shop);
        return shop;
    }

    @Override
    public Shop getOpenShop(Integer ownerId, String shopType, Integer shopId) {
        // 商城资源只属于本商家旗舰店：请求里带了别的 shopId 直接 403，防止把商品或订单挂到他人店铺。
        if (ShopType.MALL.getCode().equals(shopType)) {
            Shop flagship = getOpenFlagship(ownerId);
            if (shopId != null && !shopId.equals(flagship.getId())) {
                throw new BusinessException(403, "商城资源只能属于旗舰店");
            }
            return flagship;
        }
        if (!ShopType.SERVICE.getCode().equals(shopType)) {
            throw new BusinessException("店铺类型不合法");
        }
        // 线下服务必须落到具体门店：缺 shopId 时无法确定履约地点，不能退化成旗舰店。
        if (shopId == null) {
            throw new BusinessException("请选择线下服务门店");
        }
        return getOpenServiceOutlet(shopId, ownerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Shop createFlagship(Integer ownerId, UpdateMerchantShopRequest request) {
        // 一个商家只允许一个旗舰店：这里的查重只负责给出可读提示，并发下由 uk_shops_one_flagship 兜底（该索引仅在 type = 'mall' 且未归档时对 owner_id 生效）。
        if (shopMapper.selectFlagshipByOwner(ownerId) != null) {
            throw new BusinessException("每个商家只能创建一个旗舰店");
        }
        Shop shop = buildShop(ownerId, ShopType.MALL.getCode(), request);
        // 旗舰店是组织根节点：flagship_id 留空，服务门店才用该列指回本店，避免自引用外键。
        shop.setFlagshipId(null);
        // 商家可以不填编号，缺省用 F-<ownerId> 生成：uk_shops_owner_code 以 (owner_id, shop_code) 为唯一键，缺省值不会与自己其它店铺冲突。
        shop.setShopCode(request.getShopCode() == null || request.getShopCode().isBlank()
                ? "F-" + ownerId : request.getShopCode().trim());
        try {
            shopMapper.insert(shop);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("旗舰店已存在，请勿重复创建");
        }
        // 历史数据里可能存在先建门店、后建旗舰店的商家：新旗舰店落库后把这些旗舰店 ID 为空的存量门店统一回挂。
        shopMapper.attachOrphanServiceOutlets(ownerId, shop.getId());
        return shop;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Shop createServiceOutlet(Integer ownerId, UpdateMerchantShopRequest request) {
        // 门店只挂在已营业的旗舰店下：getOpenFlagship 未通过时直接 403，避免组织树挂到待审核或被关闭的商城店铺。
        Shop flagship = getOpenFlagship(ownerId);
        Shop shop = buildShop(ownerId, ShopType.SERVICE.getCode(), request);
        shop.setFlagshipId(flagship.getId());
        // 编号可由商家自定义，缺省生成 S- 前缀随机串；重复时由 (owner_id, shop_code) 唯一索引拦下。
        shop.setShopCode(request.getShopCode() == null || request.getShopCode().isBlank()
                ? "S-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                : request.getShopCode().trim());
        try {
            shopMapper.insert(shop);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("门店编号已存在");
        }
        return shop;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Shop createShop(Integer ownerId, UpdateMerchantShopRequest request) {
        // 先校验类型再分流：未知类型必须在进入 else 分支前被拒，否则会被当成服务门店建出来。
        validateShopType(request.getType());
        return ShopType.MALL.getCode().equals(request.getType())
                ? createFlagship(ownerId, request) : createServiceOutlet(ownerId, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Shop updateFlagship(Integer ownerId, UpdateMerchantShopRequest request) {
        Shop shop = getFlagship(ownerId);
        applyEditableFields(shop, request, true);
        shopMapper.update(shop);
        // 被驳回的店铺改完资料自动回到待审核：状态重置与资料更新在同一事务内，商家不用重新走创建流程。
        resubmitRejectedShop(shop);
        return shop;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Shop updateServiceOutlet(Integer ownerId, Integer outletId,
                                    UpdateMerchantShopRequest request) {
        Shop shop = getOwnedServiceOutlet(outletId, ownerId);
        applyEditableFields(shop, request, true);
        shopMapper.update(shop);
        resubmitRejectedShop(shop);
        return shop;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Shop updateShop(Integer ownerId, UpdateMerchantShopRequest request) {
        if (request.getId() == null) {
            throw new BusinessException("请指定要编辑的店铺ID");
        }
        Shop shop = getOwnedShop(request.getId(), ownerId);
        // 第三个参数决定 shop_code 是否可改：旗舰店允许改编号，服务门店编号创建后保持稳定，避免被改成与旗舰店重号。
        applyEditableFields(shop, request, ShopType.MALL.getCode().equals(shop.getType()));
        shopMapper.update(shop);
        resubmitRejectedShop(shop);
        return shop;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOwnedServiceOutletStatus(Integer ownerId, Integer outletId,
                                               String status) {
        // 商家可写的营业状态只有 open / closed：审核结论属于后台权限，若允许商家直接写就等于绕过审核开店。
        if (!ShopStatus.OPEN.getCode().equals(status)
                && !ShopStatus.CLOSED.getCode().equals(status)) {
            throw new BusinessException("门店状态不合法");
        }
        Shop shop = getOwnedServiceOutlet(outletId, ownerId);
        // 审核未结束的门店不具备营业资格：待审核和被驳回都先挡住，避免商家在审核期间自行开门。
        if (ShopStatus.PENDING_AUDIT.getCode().equals(shop.getStatus())) {
            throw new BusinessException(403, "门店审核通过后才能切换营业状态");
        }
        if (ShopStatus.REJECTED.getCode().equals(shop.getStatus())) {
            throw new BusinessException(403, "门店审核未通过，请修改资料后重新提交审核");
        }
        // 条件更新影响 0 行说明门店已被并发归档，按 404 返回，不能当作切换成功。
        if (shopMapper.updateStatus(shop.getId(), status) == 0) {
            throw new BusinessException(404, "服务门店不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFlagshipStatus(Integer ownerId, String status) {
        if (!ShopStatus.OPEN.getCode().equals(status)
                && !ShopStatus.CLOSED.getCode().equals(status)) {
            throw new BusinessException("店铺状态不合法");
        }
        Shop shop = getFlagship(ownerId);
        if (ShopStatus.PENDING_AUDIT.getCode().equals(shop.getStatus())) {
            throw new BusinessException("店铺审核通过后才能切换营业状态");
        }
        if (ShopStatus.REJECTED.getCode().equals(shop.getStatus())) {
            throw new BusinessException(403, "店铺审核未通过，请修改资料后重新提交审核");
        }
        if (shopMapper.updateStatus(shop.getId(), status) == 0) {
            throw new BusinessException(404, "店铺不存在");
        }
    }

    private Shop buildShop(Integer ownerId, String type, UpdateMerchantShopRequest request) {
        Shop shop = new Shop();
        shop.setOwnerId(ownerId);
        shop.setName(request.getName().trim());
        shop.setType(type);
        // 新店一律落 pending_audit，销量与评分从 0 起算：开业结论和交易数据只能由后台审核与真实成交写入。
        shop.setStatus(ShopStatus.PENDING_AUDIT.getCode());
        shop.setLogo(defaultString(request.getLogo()));
        shop.setDescription(defaultString(request.getDescription()));
        shop.setBusinessHours(defaultString(request.getBusinessHours()));
        shop.setAddress(defaultString(request.getAddress()));
        shop.setContactPhone(defaultString(request.getContactPhone()));
        shop.setTotalSales(BigDecimal.ZERO);
        shop.setRating(BigDecimal.ZERO);
        return shop;
    }

    private void applyEditableFields(Shop shop, UpdateMerchantShopRequest request,
                                     boolean updateShopCode) {
        // 字段为 null 视为本次不修改：部分更新语义，避免前端只提交流程中的少数几项时把地址、营业时间清空。
        if (request.getName() != null) {
            shop.setName(request.getName().trim());
        }
        if (request.getLogo() != null) {
            shop.setLogo(request.getLogo());
        }
        if (request.getDescription() != null) {
            shop.setDescription(request.getDescription());
        }
        if (request.getAddress() != null) {
            shop.setAddress(request.getAddress());
        }
        if (request.getBusinessHours() != null) {
            shop.setBusinessHours(request.getBusinessHours());
        }
        if (request.getContactPhone() != null) {
            shop.setContactPhone(request.getContactPhone().trim());
        }
        if (updateShopCode && request.getShopCode() != null
                && !request.getShopCode().isBlank()) {
            shop.setShopCode(request.getShopCode().trim());
        }
    }

    @Override
    public PageResult<Shop> listShops(String status, String type, Integer merchantId, String keyword,
                                      int page, int pageSize) {
        // page / pageSize 直接来自请求参数，先归一化再算 offset：PageUtils 把 page 下限压到 1、pageSize 上限压到 100，防止负偏移和一次拉全表。
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(pageSize);
        int offset = (safePage - 1) * safePageSize;
        List<Shop> list = shopMapper.selectAll(status, type, merchantId, keyword, offset, safePageSize);
        int total = shopMapper.countAll(status, type, merchantId, keyword);
        return new PageResult<>(list, total, safePage, safePageSize);
    }

    @Override
    public Shop getShopDetail(Integer shopId) {
        Shop shop = shopMapper.selectByIdWithMerchant(shopId);
        if (shop == null) {
            throw new BusinessException(404, "店铺不存在");
        }
        return shop;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditShop(Integer shopId, String status) {
        if (!ShopStatus.OPEN.getCode().equals(status)
                && !ShopStatus.REJECTED.getCode().equals(status)) {
            throw new BusinessException("审核状态不合法");
        }
        Shop shop = getShopDetail(shopId);
        // 只有待审核店铺能进入审核：已营业或已驳回的店铺再审一次会覆盖人工结论，也会打乱后续营业状态流转。
        if (!ShopStatus.PENDING_AUDIT.getCode().equals(shop.getStatus())) {
            throw new BusinessException("仅待审核店铺可以执行审核");
        }
        if (shopMapper.updateStatus(shopId, status) == 0) {
            throw new BusinessException(404, "店铺不存在");
        }
        // 审核通过时同步解锁商家账号：管理员可能因违规停用过该账号，店铺开业后商家必须能登录经营。
        if (ShopStatus.OPEN.getCode().equals(status)) {
            enableMerchant(shop.getOwnerId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShopStatus(Integer shopId, String status) {
        if (!ShopStatus.OPEN.getCode().equals(status)
                && !ShopStatus.CLOSED.getCode().equals(status)
                && !ShopStatus.PENDING_AUDIT.getCode().equals(status)) {
            throw new BusinessException("店铺状态不合法");
        }
        Shop shop = getShopDetail(shopId);
        // 待审核店铺只能走审核接口转 open：这里拦住“直接改状态跳过审核”的旁路，驳回店铺则必须先改资料重新提交。
        if (ShopStatus.OPEN.getCode().equals(status)
                && ShopStatus.PENDING_AUDIT.getCode().equals(shop.getStatus())) {
            throw new BusinessException("待审核店铺请通过审核操作处理");
        }
        if (ShopStatus.OPEN.getCode().equals(status)
                && ShopStatus.REJECTED.getCode().equals(shop.getStatus())) {
            throw new BusinessException("审核未通过店铺需修改后重新提交审核");
        }
        if (shopMapper.updateStatus(shopId, status) == 0) {
            throw new BusinessException(404, "店铺不存在");
        }
        // 恢复营业与审核通过同样需要解锁商家账号，两条入口的补偿必须一致，否则商家会被锁在登录之外。
        if (ShopStatus.OPEN.getCode().equals(status)) {
            enableMerchant(shop.getOwnerId());
        }
    }

    private void enableMerchant(Integer ownerId) {
        if (ownerId != null) {
            userMapper.updateStatus(ownerId, AccountStatus.NORMAL.getCode());
        }
    }

    private void requireOpen(Shop shop) {
        // 营业状态是所有对外入口的共同前置条件，非 open 统一 403；关店只挡新交易，已产生的订单与预约仍按 shop_id 保留。
        if (!ShopStatus.OPEN.getCode().equals(shop.getStatus())) {
            throw new BusinessException(403, "店铺正在审核中或已关闭");
        }
    }

    private void validateShopType(String shopType) {
        if (!ShopType.MALL.getCode().equals(shopType)
                && !ShopType.SERVICE.getCode().equals(shopType)) {
            throw new BusinessException("店铺类型不合法");
        }
    }

    private void resubmitRejectedShop(Shop shop) {
        if (ShopStatus.REJECTED.getCode().equals(shop.getStatus())) {
            shopMapper.updateStatus(shop.getId(), ShopStatus.PENDING_AUDIT.getCode());
            shop.setStatus(ShopStatus.PENDING_AUDIT.getCode());
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
