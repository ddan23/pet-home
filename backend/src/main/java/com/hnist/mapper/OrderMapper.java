package com.hnist.mapper;

import com.hnist.entity.Order;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 订单表数据访问：商品单/预约单查询、状态流转与营业额统计。 */
public interface OrderMapper {

    // 根据订单ID查询订单详情
    Order selectById(@Param("id") Integer id);

    // 分页查询用户订单列表（按状态和订单类型筛选）
    List<Order> selectByUserId(@Param("userId") Integer userId,
                               @Param("status") String status,
                               @Param("orderType") String orderType,
                               @Param("offset") Integer offset,
                               @Param("limit") Integer limit);

    // 统计用户订单数量
    int countByUserId(@Param("userId") Integer userId,
                      @Param("status") String status,
                      @Param("orderType") String orderType);

    // 分页查询商家订单列表
    List<Order> selectByShopId(@Param("shopId") Integer shopId,
                               @Param("status") String status,
                               @Param("orderType") String orderType,
                               @Param("keyword") String keyword,
                               @Param("offset") Integer offset,
                               @Param("limit") Integer limit);

    // 统计商家订单数量
    int countByShopId(@Param("shopId") Integer shopId,
                      @Param("status") String status,
                      @Param("orderType") String orderType,
                      @Param("keyword") String keyword);

    // 商家总部视角：按 shops.owner_id 隔离，可再按 shopId/userId 缩小范围
    List<Order> selectByOwner(@Param("ownerId") Integer ownerId,
                              @Param("shopId") Integer shopId,
                              @Param("userId") Integer userId,
                              @Param("status") String status,
                              @Param("orderType") String orderType,
                              @Param("offset") Integer offset,
                              @Param("limit") Integer limit);

    int countByOwner(@Param("ownerId") Integer ownerId,
                     @Param("shopId") Integer shopId,
                     @Param("userId") Integer userId,
                     @Param("status") String status,
                     @Param("orderType") String orderType);

    // 创建新订单
    int insert(Order order);

    // 更新订单状态
    int updateStatus(@Param("id") Integer id, @Param("status") String status);

    // 仅当订单仍处于预期状态时更新，避免取消和释放在并发下重复执行
    int updateStatusIfCurrent(@Param("id") Integer id,
                              @Param("expectedStatus") String expectedStatus,
                              @Param("status") String status);

    // 查询所有过期的预约订单（已预约状态且预约时间已过）
    List<Order> selectExpiredBookings();

    // 查询某服务某天全部已确认且预约时间未到的预约订单：休业前取消预约并通知用户的数据来源
    List<Order> selectConfirmedBookingsForDay(
            @Param("serviceId") Integer serviceId,
            @Param("shopId") Integer shopId,
            @Param("slotDate") java.time.LocalDate slotDate);

    // 查询指定时间窗口内即将开始的预约
    List<Order> selectStartingBetween(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to);

    // 查询创建超过支付时限的待支付商品订单
    List<Order> selectExpiredPendingProductOrders(
            @Param("createdBefore") java.time.LocalDateTime createdBefore);

    // 批量将过期预约订单状态更新为已取消
    int batchCancelExpiredBookings();

    // 查询店铺已完成订单的营业额（按订单类型区分商品/服务）
    java.math.BigDecimal getShopRevenue(@Param("shopId") Integer shopId, @Param("orderType") String orderType);

    java.math.BigDecimal getOwnerRevenue(@Param("ownerId") Integer ownerId,
                                         @Param("shopId") Integer shopId,
                                         @Param("orderType") String orderType);

    // 已完成交易数（与营业额同口径：status = completed）
    int countShopCompletedOrders(@Param("shopId") Integer shopId,
                                 @Param("orderType") String orderType);

    int countOwnerCompletedOrders(@Param("ownerId") Integer ownerId,
                                  @Param("shopId") Integer shopId,
                                  @Param("orderType") String orderType);
}
