package com.hnist.controller;

import com.hnist.annotation.CurrentUser;
import com.hnist.annotation.RequireRole;
import com.hnist.common.result.Result;
import com.hnist.common.constant.AccountRole;
import com.hnist.common.constant.OrderStatus;
import com.hnist.common.constant.OrderType;
import com.hnist.common.constant.ShopType;
import com.hnist.common.exception.BusinessException;
import com.hnist.common.result.PageResult;
import com.hnist.vo.BookingCreationResponse;
import com.hnist.dto.CreateBookingRequest;
import com.hnist.dto.CreateOrderRequest;
import com.hnist.dto.DirectPurchaseRequest;
import com.hnist.vo.MerchantRevenueSummary;
import com.hnist.vo.OrderCreationResponse;
import com.hnist.entity.Order;
import com.hnist.entity.Shop;
import com.hnist.entity.User;
import com.hnist.service.OrderService;
import com.hnist.service.ShopService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** 订单与预约控制器。 */
@RestController
@RequestMapping({"/api", "/api/v1"})
public class OrderController {

    private final OrderService orderService;
    private final ShopService shopService;

    public OrderController(OrderService orderService, ShopService shopService) {
        this.orderService = orderService;
        this.shopService = shopService;
    }

    @GetMapping("/orders")
    @RequireRole(AccountRole.USER_CODE)
    public Result<PageResult<Order>> listUserOrders(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "orderType", required = false) String orderType,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @CurrentUser User user) {
        return Result.ok(orderService.getUserOrders(user.getId(), status, orderType, page, pageSize));
    }

    /** 购物车整单结算：跨店铺拆成多笔订单（orderIds），失效商品自动跳过并在 skippedCount 中返回。 */
    @PostMapping("/orders")
    @RequireRole(AccountRole.USER_CODE)
    public Result<OrderCreationResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @CurrentUser User user) {
        OrderCreationResponse response =
                orderService.createOrder(user.getId(), request.getShippingAddress());
        String message = response.getSkippedCount() > 0
                ? "下单成功，" + response.getSkippedCount() + " 件失效商品未参与结算"
                : "下单成功";
        return Result.ok(message, response);
    }

    @PostMapping("/orders/direct")
    @RequireRole(AccountRole.USER_CODE)
    public Result<OrderCreationResponse> directPurchase(
            @Valid @RequestBody DirectPurchaseRequest request,
            @CurrentUser User user) {
        return Result.ok("购买成功", orderService.directPurchase(
                user.getId(), request.getProductId(),
                request.getQuantity(), request.getShippingAddress()));
    }

    /** 预约下单：占用时段数与金额均由服务端计算（服务单价 × slotCount），不接受前端传金额。 */
    @PostMapping("/orders/booking")
    @RequireRole(AccountRole.USER_CODE)
    public Result<BookingCreationResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @CurrentUser User user) {
        return Result.ok("预约成功", orderService.createBooking(
                user.getId(), request.getServiceId(), request.getPetId(),
                request.getSlotIds(), request.getNote()));
    }

    @PutMapping("/orders/{id}/pay")
    @RequireRole(AccountRole.USER_CODE)
    public Result<Void> payOrder(@PathVariable Integer id, @CurrentUser User user) {
        orderService.payOrder(id, user.getId());
        return Result.ok();
    }

    @PutMapping("/orders/{id}/cancel")
    @RequireRole(AccountRole.USER_CODE)
    public Result<Void> cancelOrder(@PathVariable Integer id, @CurrentUser User user) {
        orderService.cancelOrder(id, user.getId());
        return Result.ok();
    }

    @PutMapping("/orders/{id}/remind-shipment")
    @RequireRole(AccountRole.USER_CODE)
    public Result<Void> remindShipment(@PathVariable Integer id,
                                       @CurrentUser User user) {
        orderService.remindShipment(id, user.getId());
        return Result.ok("已提醒商家发货", null);
    }

    @PutMapping("/orders/{id}/confirm")
    @RequireRole(AccountRole.USER_CODE)
    public Result<Void> confirmReceived(@PathVariable Integer id, @CurrentUser User user) {
        orderService.confirmReceived(id, user.getId());
        return Result.ok();
    }

    @GetMapping("/merchant/orders")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<PageResult<Order>> listMerchantOrders(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "userId", required = false) Integer customerId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
            @CurrentUser User user) {
        Shop shop = shopService.getOpenFlagship(user.getId());
        // 消息中心按客户查看订单时，范围限定为当前商家的旗舰商城
        if (customerId != null) {
            return Result.ok(orderService.getMerchantOrdersByOwner(
                    user.getId(), shop.getId(), status, OrderType.PRODUCT.getCode(),
                    page, pageSize, customerId));
        }
        return Result.ok(orderService.getMerchantOrders(
                shop.getId(), status, OrderType.PRODUCT.getCode(), keyword, page, pageSize));
    }

    @PutMapping("/merchant/orders/{id}/ship")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Void> shipOrder(
            @PathVariable Integer id,
            @CurrentUser User user) {
        Shop shop = shopService.getOpenFlagship(user.getId());
        orderService.shipOrder(id, shop.getId());
        return Result.ok();
    }

    /** outletId 非空时先校验其归属（必须是本人的服务门店），再按该门店过滤预约单。 */
    @GetMapping("/merchant/appointments")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<PageResult<Order>> listMerchantAppointments(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "outletId", required = false) Integer outletId,
            @RequestParam(name = "userId", required = false) Integer customerId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @CurrentUser User user) {
        if (outletId != null) {
            shopService.getOpenServiceOutlet(outletId, user.getId());
        }
        return Result.ok(orderService.getMerchantOrdersByOwner(
                user.getId(), outletId, status, OrderType.BOOKING.getCode(),
                page, pageSize, customerId));
    }

    /** type=mall 统计本人旗舰商城、type=service 统计指定门店（需归属校验），其他值返回业务码 400。 */
    @GetMapping("/merchant/revenue")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<BigDecimal> getMerchantRevenue(
            @RequestParam(name = "type") String type,
            @RequestParam(name = "outletId", required = false) Integer outletId,
            @CurrentUser User user) {
        if (ShopType.MALL.getCode().equals(type)) {
            Shop flagship = shopService.getOwnedShop(
                    shopService.getFlagship(user.getId()).getId(), user.getId());
            return Result.ok(orderService.getShopRevenue(
                    flagship.getId(), OrderType.PRODUCT.getCode()));
        }
        if (!ShopType.SERVICE.getCode().equals(type)) {
            return Result.fail(400, "营业额类型不合法");
        }
        if (outletId != null) {
            shopService.getOwnedServiceOutlet(outletId, user.getId());
        }
        return Result.ok(orderService.getMerchantRevenue(
                user.getId(), outletId, OrderType.BOOKING.getCode()));
    }

    @GetMapping("/merchant/revenue/summary")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<MerchantRevenueSummary> getMerchantRevenueSummary(
            @RequestParam(name = "type") String type,
            @RequestParam(name = "outletId", required = false) Integer outletId,
            @CurrentUser User user) {
        if (ShopType.MALL.getCode().equals(type)) {
            Shop flagship = shopService.getOwnedShop(
                    shopService.getFlagship(user.getId()).getId(), user.getId());
            return Result.ok(new MerchantRevenueSummary(
                    orderService.getShopRevenue(flagship.getId(), OrderType.PRODUCT.getCode()),
                    orderService.countShopCompletedOrders(
                            flagship.getId(), OrderType.PRODUCT.getCode())));
        }
        if (!ShopType.SERVICE.getCode().equals(type)) {
            return Result.fail(400, "营业额类型不合法");
        }
        if (outletId != null) {
            shopService.getOwnedServiceOutlet(outletId, user.getId());
        }
        return Result.ok(new MerchantRevenueSummary(
                orderService.getMerchantRevenue(user.getId(), outletId, OrderType.BOOKING.getCode()),
                orderService.countMerchantCompletedOrders(
                        user.getId(), outletId, OrderType.BOOKING.getCode())));
    }

    @PutMapping("/merchant/appointments/{id}/complete")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Void> completeAppointment(
            @PathVariable Integer id,
            @RequestParam(name = "outletId", required = false) Integer outletId,
            @CurrentUser User user) {
        Order order = requireOwnedAppointment(id, outletId, user.getId());
        orderService.updateAppointmentStatus(
                id, order.getShopId(), user.getId(),
                OrderStatus.COMPLETED.getCode());
        return Result.ok();
    }

    @PutMapping("/merchant/appointments/{id}/cancel")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<Void> cancelAppointment(
            @PathVariable Integer id,
            @RequestParam(name = "outletId", required = false) Integer outletId,
            @CurrentUser User user) {
        Order order = requireOwnedAppointment(id, outletId, user.getId());
        orderService.updateAppointmentStatus(
                id, order.getShopId(), user.getId(),
                OrderStatus.CANCELLED.getCode());
        return Result.ok();
    }

    /** 预约不存在、非预约类型或与 outletId 不一致时统一抛 404，不暴露资源存在性。 */
    private Order requireOwnedAppointment(Integer orderId, Integer outletId, Integer ownerId) {
        Order order = orderService.getOrderById(orderId);
        if (order == null
                || !OrderType.BOOKING.getCode().equals(order.getOrderType())
                || (outletId != null && !outletId.equals(order.getShopId()))) {
            throw new BusinessException(404, "预约不存在");
        }
        shopService.getOwnedServiceOutlet(order.getShopId(), ownerId);
        return order;
    }
}
