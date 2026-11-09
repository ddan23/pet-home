package com.hnist.mapper;

import com.hnist.entity.Product;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品/服务数据访问层 - 商品表CRUD操作
 */
public interface ProductMapper {

    // 根据商品ID查询商品详情
    Product selectById(@Param("id") Integer id);

    // 查询可公开访问的商品或服务详情
    Product selectPublicById(@Param("id") Integer id,
                             @Param("productType") String productType);

    // 分页查询商城商品列表（仅上架商品）
    List<Product> selectForUser(@Param("categoryFilter") String categoryFilter,
                                @Param("keyword") String keyword,
                                @Param("shopId") Integer shopId,
                                @Param("offset") Integer offset,
                                @Param("limit") Integer limit);

    // 统计商城商品数量
    int countForUser(@Param("categoryFilter") String categoryFilter,
                     @Param("keyword") String keyword,
                     @Param("shopId") Integer shopId);

    // 分页查询服务列表（仅上架服务）
    List<Product> selectServicesForUser(@Param("categoryFilter") String categoryFilter,
                                        @Param("keyword") String keyword,
                                                @Param("shopId") Integer shopId,
                                        @Param("offset") Integer offset,
                                        @Param("limit") Integer limit);

    // 统计服务数量
    int countServicesForUser(@Param("categoryFilter") String categoryFilter,
                             @Param("keyword") String keyword,
                                     @Param("shopId") Integer shopId);

    // 商家端分页查询；带 stockMax 时按 stock 升序优先展示低库存商品
    List<Product> selectByShopId(@Param("shopId") Integer shopId,
                                 @Param("keyword") String keyword,
                                 @Param("categoryFilter") String categoryFilter,
                                 @Param("status") String status,
                                 @Param("stockMax") Integer stockMax,
                                 @Param("productType") String productType,
                                 @Param("offset") Integer offset,
                                 @Param("limit") Integer limit);

    // 统计商家商品/服务数量
    int countByShopId(@Param("shopId") Integer shopId,
                      @Param("keyword") String keyword,
                      @Param("categoryFilter") String categoryFilter,
                      @Param("status") String status,
                      @Param("stockMax") Integer stockMax,
                      @Param("productType") String productType);

    // 总部视角查询商家全部门店服务
    List<Product> selectServicesByOwner(@Param("ownerId") Integer ownerId,
                                        @Param("keyword") String keyword,
                                        @Param("categoryFilter") String categoryFilter,
                                        @Param("status") String status,
                                        @Param("offset") Integer offset,
                                        @Param("limit") Integer limit);

    int countServicesByOwner(@Param("ownerId") Integer ownerId,
                             @Param("keyword") String keyword,
                             @Param("categoryFilter") String categoryFilter,
                             @Param("status") String status);

    // 分页查询所有商品/服务（管理员视角）
    List<Product> selectAllForAdmin(@Param("keyword") String keyword,
                                    @Param("status") String status,
                                    @Param("productType") String productType,
                                    @Param("offset") Integer offset,
                                    @Param("limit") Integer limit);

    // 统计所有商品/服务数量
    int countAllForAdmin(@Param("keyword") String keyword,
                         @Param("status") String status,
                         @Param("productType") String productType);

    // 新增商品/服务
    int insert(Product product);

    // 更新商品/服务信息
    int update(Product product);

    // 删除商品/服务
    int deleteById(@Param("id") Integer id);

    // 删除前校验：是否存在该服务的周排班模板（fk_schedule_service 指向 products，无级联，直接删会外键报错）
    boolean existsScheduleByServiceId(@Param("serviceId") Integer serviceId);

    // 删除前校验/孤儿清理：是否存在该服务已生成的可预约时段（fk_slot_service 指向 products，无级联）
    boolean existsSlotByServiceId(@Param("serviceId") Integer serviceId);

    // 删除前校验：时段是否被预约订单引用（order_appointment_slots.slot_id → appointment_slots，无级联，历史履约凭证必须保留）
    boolean existsOrderRefByServiceId(@Param("serviceId") Integer serviceId);

    // 清理无预约引用的孤儿时段：仅在校验通过后与删商品同事务执行，被预约引用的时段已在前面分支拦下
    int deleteSlotsByServiceId(@Param("serviceId") Integer serviceId);

    // 更新商品/服务状态（上架/下架）
    int updateStatus(@Param("id") Integer id, @Param("status") String status);

    // 条件扣减库存：仅 status = 'on' 且 stock >= quantity 才扣减，扣到 0 时自动置 status = 'off' 下架；返回 0 表示库存不足
    int reserveStock(@Param("productId") Integer productId,
                     @Param("quantity") Integer quantity);

    // 释放订单预留的商品库存
    int releaseStock(@Param("productId") Integer productId,
                     @Param("quantity") Integer quantity);

}
