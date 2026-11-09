package com.hnist.service;

import com.hnist.vo.OrderCreationResponse;
import com.hnist.vo.BookingCreationResponse;
import com.hnist.entity.Order;
import com.hnist.common.result.PageResult;

/** 订单与预约的统一服务入口：创建、支付、取消、履约处理及商家侧查询。 */
public interface OrderService {

    // 只返回该 userId 的订单；orderType 为 booking 时会先触发过期预约回收，属于带副作用的查询。
    PageResult<Order> getUserOrders(Integer userId, String status, String orderType, int page, int pageSize);

    // 购物车结算：商品按店铺拆成多笔订单返回；任一店铺库存不足整批回滚，成功后删除已结算的购物车行。
    OrderCreationResponse createOrder(Integer userId, String shippingAddress);

    // 跳过购物车直接下单；库存仍由条件更新裁决，返回单笔订单。
    OrderCreationResponse directPurchase(Integer userId, Integer productId,
                                         Integer quantity, String shippingAddress);

    // 预约下单：占用时段名额并生成 booking 订单，名额不足即整单失败（实现在 AppointmentScheduleServiceImpl）。
    BookingCreationResponse createBooking(Integer userId, Integer serviceId, Integer petId,
                                          java.util.List<Integer> slotIds, String note);

    // 只有 pending_payment 且未超过 10 分钟支付时限的订单可支付；状态被并发改写时抛出异常而不是静默成功。
    void payOrder(Integer orderId, Integer userId);

    // 用户取消：商品订单回补库存，预约订单释放时段名额，资源归还与状态变更同事务。
    void cancelOrder(Integer orderId, Integer userId);

    // 提醒商家发货；同一订单同一天最多产生一条通知（幂等键含当天日期）。
    void remindShipment(Integer orderId, Integer userId);

    // 只有 shipped 可确认收货，成功后订单进入 completed。
    void confirmReceived(Integer orderId, Integer userId);

    // 商家订单列表，按 shopId 隔离，shopId 必须由登录态推导而不是请求参数。
    PageResult<Order> getMerchantOrders(Integer shopId, String status, String orderType,
                                        String keyword, int page, int pageSize);

    // 总部跨门店查询：ownerId 决定可见范围，shopId 与 customerId 是可选过滤条件。
    PageResult<Order> getMerchantOrdersByOwner(Integer ownerId, Integer shopId, String status,
                                               String orderType, int page, int pageSize,
                                               Integer customerId);

    // 只有 paid 可发货，且订单必须属于该 shopId。
    void shipOrder(Integer orderId, Integer shopId);

    // 商家只能把 confirmed 预约置为 completed 或 cancelled；取消时同步释放时段名额。
    void updateAppointmentStatus(Integer orderId, Integer shopId,
                                 Integer operatorId, String status);

    // 按 id 直查，不做归属校验，调用方必须自行确认访问权限。
    Order getOrderById(Integer orderId);

    // 只累计 completed 状态的订单，无数据返回 0。
    java.math.BigDecimal getShopRevenue(Integer shopId, String orderType);

    // 按 ownerId 汇总名下门店营业额，shopId 为空表示全部门店。
    java.math.BigDecimal getMerchantRevenue(Integer ownerId, Integer shopId, String orderType);

    // 只统计 completed 订单，与营业额同口径。
    int countShopCompletedOrders(Integer shopId, String orderType);

    // 与 countShopCompletedOrders 同口径，按 ownerId 汇总名下门店。
    int countMerchantCompletedOrders(Integer ownerId, Integer shopId, String orderType);

    // 供定时任务调用：取消超时未支付的商品订单并释放库存，返回实际取消笔数。
    int cancelExpiredPendingOrders();

    // 供 MQ 关单消费者调用：按 id 取消超时未支付的商品订单并释放库存；订单不存在、非商品订单或状态已变化时静默返回。
    void cancelExpiredPendingOrder(Integer orderId);

    /** 为两小时内即将开始的预约生成提醒通知，重复执行由幂等键去重。 */
    void remindUpcomingAppointments();
}
