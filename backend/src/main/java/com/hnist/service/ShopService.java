package com.hnist.service;

import com.hnist.dto.UpdateMerchantShopRequest;
import com.hnist.vo.MerchantOrganizationResponse;
import com.hnist.common.result.PageResult;
import com.hnist.entity.Shop;

import java.util.List;

/** 商家店铺组织（旗舰店与服务门店）的归属校验、创建、编辑和审核。 */
public interface ShopService {

    List<Shop> listOwnedShops(Integer ownerId);

    // 组织视图允许旗舰店为 null：商家还没建店时也要能返回结构，服务门店按创建顺序列出，这里不做 403 拦截。
    MerchantOrganizationResponse getOrganization(Integer ownerId);

    // 商家后台读取自有店铺：失败一律回 404“店铺不存在”，越权与不存在不对外区分。
    Shop getOwnedShop(Integer shopId, Integer ownerId);

    // 不存在时抛 403，提示先创建旗舰店；允许旗舰店为空的场景改用 getOrganization。
    Shop getFlagship(Integer ownerId);

    // 对外写入（上架、下单入口）只认营业中的旗舰店，待审核、驳回、关闭一律 403。
    Shop getOpenFlagship(Integer ownerId);

    List<Shop> listServiceOutlets(Integer ownerId);

    // 只校验归属（404），不看营业状态：商家自管后台用它读门店。
    Shop getOwnedServiceOutlet(Integer outletId, Integer ownerId);

    // 归属校验之上再要求 open：预约排班、门店商品等对外入口不能落到停业门店。
    Shop getOpenServiceOutlet(Integer outletId, Integer ownerId);

    // 下单类入口按 shopType 分流：mall 使用本商家旗舰店，service 必须显式指定 outletId。
    Shop getOpenShop(Integer ownerId, String shopType, Integer shopId);

    // 每个商家只能有一个生效旗舰店，重复创建返回业务错误，不会落第二条记录。
    Shop createFlagship(Integer ownerId, UpdateMerchantShopRequest request);

    // 前置条件：商家已有审核通过的旗舰店，新门店挂到该旗舰店下。
    Shop createServiceOutlet(Integer ownerId, UpdateMerchantShopRequest request);

    // 按 request.getType() 分流：mall 走 createFlagship，其余合法类型建服务门店，未知类型直接拒绝。
    Shop createShop(Integer ownerId, UpdateMerchantShopRequest request);

    // 被驳回的旗舰店改完资料自动回到待审核，无需重新走创建流程。
    Shop updateFlagship(Integer ownerId, UpdateMerchantShopRequest request);

    Shop updateServiceOutlet(Integer ownerId, Integer outletId,
                             UpdateMerchantShopRequest request);

    // 编辑按请求体 id 定位并校验归属；只有旗舰店允许改 shop_code，服务门店编号保持稳定。
    Shop updateShop(Integer ownerId, UpdateMerchantShopRequest request);

    // 商家只可切 open / closed：待审核或被驳回的门店不能自行开门营业。
    void updateOwnedServiceOutletStatus(Integer ownerId, Integer outletId, String status);

    // 商家自管开关门只接受 open / closed：审核结论由后台审核接口写入，商家不能自己把店铺置成待审核或已驳回。
    void updateFlagshipStatus(Integer ownerId, String status);

    // 后台分页口径：status、type、merchantId、keyword 全部下推 SQL，total 用同一组条件统计，翻页时总数不漂移。
    PageResult<Shop> listShops(String status, String type, Integer merchantId, String keyword,
                               int page, int pageSize);

    // 后台详情按 id 直查并带出商家名，刻意不过滤状态：审核要看得到待审核与已关闭的店铺。
    Shop getShopDetail(Integer shopId);

    // 审核起点只能是 pending_audit；置为 open 时同时解锁商家账号。
    void auditShop(Integer shopId, String status);

    // 后台强制改状态不允许成为审核旁路：待审核走 auditShop，已驳回必须先改资料重新提交。
    void updateShopStatus(Integer shopId, String status);
}
