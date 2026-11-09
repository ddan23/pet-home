package com.hnist.mapper;

import com.hnist.entity.Shop;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 店铺表数据访问：门店档案、营业状态与商家归属管理。 */
public interface ShopMapper {

    // 根据店铺ID查询店铺信息
    Shop selectById(@Param("id") Integer id);

    // 公开口径：仅 status = 'open'、未归档且商家账号 status = 'normal' 的店铺对外可见
    Shop selectPublicById(@Param("id") Integer id);

    // 根据店铺ID查询店铺信息（关联查询商家信息）
    Shop selectByIdWithMerchant(@Param("id") Integer id);

    // 根据商家ID查询单个店铺
    Shop selectByOwnerId(@Param("ownerId") Integer ownerId);

    // 查询商家有效旗舰店
    Shop selectFlagshipByOwner(@Param("ownerId") Integer ownerId);

    // 根据商家ID查询其所有店铺列表
    List<Shop> selectByOwner(@Param("ownerId") Integer ownerId);

    // 查询商家全部有效线下服务门店
    List<Shop> selectServiceOutletsByOwner(@Param("ownerId") Integer ownerId);

    // 查询商家拥有的指定服务门店
    Shop selectOwnedServiceOutlet(@Param("id") Integer id,
                                  @Param("ownerId") Integer ownerId);

    // 分页查询所有店铺列表（管理员视角，按状态/类型/商家/关键词筛选）
    List<Shop> selectAll(@Param("status") String status,
                         @Param("type") String type,
                         @Param("merchantId") Integer merchantId,
                         @Param("keyword") String keyword,
                         @Param("offset") int offset,
                         @Param("limit") int limit);

    // 统计店铺总数（与selectAll筛选条件一致）
    int countAll(@Param("status") String status,
                 @Param("type") String type,
                 @Param("merchantId") Integer merchantId,
                 @Param("keyword") String keyword);

    // 创建新店铺
    int insert(Shop shop);

    // 更新店铺信息
    int update(Shop shop);

    // 只绑定 flagship_id IS NULL 的存量服务门店，已绑定过的不受影响（可重复执行）
    int attachOrphanServiceOutlets(@Param("ownerId") Integer ownerId,
                                   @Param("flagshipId") Integer flagshipId);

    // 更新店铺状态（待审核/营业中/已关闭）
    int updateStatus(@Param("id") Integer id, @Param("status") String status);

    // 注意：该语句未过滤 archived_at，会连商家名下已归档店铺的状态一起更新
    int updateStatusByOwner(@Param("ownerId") Integer ownerId, @Param("status") String status);
}
