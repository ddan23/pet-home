package com.hnist.service.impl;

import com.hnist.common.constant.OrderStatus;
import com.hnist.common.constant.OrderType;
import com.hnist.common.constant.ProductType;
import com.hnist.common.constant.ShopStatus;
import com.hnist.common.constant.ShopType;
import com.hnist.vo.MerchantDashboardOverview;
import com.hnist.entity.AppointmentSlot;
import com.hnist.entity.Order;
import com.hnist.common.result.PageResult;
import com.hnist.entity.Product;
import com.hnist.entity.Shop;
import com.hnist.service.MerchantDashboardService;
import com.hnist.service.OrderService;
import com.hnist.service.ProductService;
import com.hnist.service.ShopService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 商家经营总览聚合：只统计名下营业中门店的商品、订单、预约与营业额指标。 */
@Service
public class MerchantDashboardServiceImpl implements MerchantDashboardService {

    // 库存 ≤ 10（含 0）计入补货预警；与 getMerchantProducts 的 stockMax 过滤同一口径，预警数与列表条数才能对上。
    private static final int LOW_STOCK_THRESHOLD = 10;
    private static final int RECENT_ORDER_LIMIT = 6;
    private static final int TODAY_APPOINTMENT_LIMIT = 6;
    private static final int SHOP_DATA_LIMIT = 100;
    private static final int TOP_WARNING_LIMIT = 5;

    private final ShopService shopService;
    private final ProductService productService;
    private final OrderService orderService;

    public MerchantDashboardServiceImpl(ShopService shopService,
                                        ProductService productService,
                                        OrderService orderService) {
        this.shopService = shopService;
        this.productService = productService;
        this.orderService = orderService;
    }

    @Override
    public MerchantDashboardOverview getOverview(Integer ownerId) {
        MerchantDashboardOverview overview = new MerchantDashboardOverview();
        // ownerId 取自登录态，店铺与订单查询都由它收窄，商家之间看不到彼此的门店数据。
        List<Shop> shops = shopService.listOwnedShops(ownerId);
        List<Order> recentOrders = new ArrayList<>();
        List<Order> todayAppointments = new ArrayList<>();
        List<Product> lowStockProducts = new ArrayList<>();
        MerchantDashboardOverview.Summary summary = overview.getSummary();
        // 计数字段是包装类型，默认 null 而非 0：累加前先归零，保持与旧基本类型字段完全一致的输出。
        summary.setActiveShopCount(0);
        summary.setProductCount(0);
        summary.setServiceCount(0);
        summary.setPendingShipments(0);
        summary.setPendingAppointments(0);
        summary.setLowStockCount(0);
        summary.setTransactionCount(0);
        summary.setTotalShopCount(shops.size());

        for (Shop shop : shops) {
            MerchantDashboardOverview.ShopOverview shopOverview = toShopOverview(shop);
            overview.getShops().add(shopOverview);
            // 门店照常列出，但只有营业中的店铺参与经营汇总：待审核、驳回、停业门店的订单不能算进营业额与待办数。
            if (!ShopStatus.OPEN.getCode().equals(shop.getStatus())) {
                continue;
            }
            summary.setActiveShopCount(summary.getActiveShopCount() + 1);
            if (ShopType.MALL.getCode().equals(shop.getType())) {
                collectMallData(shop, shopOverview, summary, recentOrders, lowStockProducts);
            } else if (ShopType.SERVICE.getCode().equals(shop.getType())) {
                collectServiceData(shop, shopOverview, summary, todayAppointments);
            }
        }

        // 多店订单是逐店追加进来的，必须重新按创建时间倒序排；nullsLast 把时间为空的脏数据压到末尾而不是抛 NPE。
        recentOrders.sort(Comparator.comparing(
                Order::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        // 今日预约按场次开始时间升序，最早要接待的排最前，商家从上往下处理。
        todayAppointments.sort(Comparator.comparing(
                this::appointmentSortValue, Comparator.nullsLast(Comparator.naturalOrder())));
        // 低库存先按库存升序，同库存再按上架时间倒序：库存一样时优先提示最近在卖的商品。
        lowStockProducts.sort(Comparator
                .comparing(Product::getStock, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(
                        Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        overview.setRecentOrders(limit(recentOrders, RECENT_ORDER_LIMIT));
        overview.setTodayAppointments(limit(todayAppointments, TODAY_APPOINTMENT_LIMIT));
        overview.setLowStockProducts(limit(lowStockProducts, TOP_WARNING_LIMIT));
        return overview;
    }

    private void collectMallData(Shop shop,
                                 MerchantDashboardOverview.ShopOverview shopOverview,
                                 MerchantDashboardOverview.Summary summary,
                                 List<Order> recentOrders,
                                 List<Product> lowStockProducts) {
        // 计数查询统一用 pageSize=1：只要 total，列表留空，避免为统计把整店商品和订单拉进内存。
        PageResult<Product> products = productService.getMerchantProducts(
                shop.getId(), null, null, null, null,
                ProductType.GOODS.getCode(), 1, 1);
        // 低库存走 SQL 的 stockMax 过滤，只取前 100 条用于展示，不做全表遍历。
        PageResult<Product> lowStock = productService.getMerchantProducts(
                shop.getId(), null, null, null, LOW_STOCK_THRESHOLD,
                ProductType.GOODS.getCode(), 1, SHOP_DATA_LIMIT);
        // 待发货口径是 paid 状态的商品订单：货已付款但还没发货，与待付款订单分开统计。
        PageResult<Order> pendingShipments = orderService.getMerchantOrders(
                shop.getId(), OrderStatus.PAID.getCode(), OrderType.PRODUCT.getCode(), null, 1, 1);
        PageResult<Order> orders = orderService.getMerchantOrders(
                shop.getId(), null, OrderType.PRODUCT.getCode(), null, 1, RECENT_ORDER_LIMIT);
        // 营业额必须由 SQL 聚合（只累加 status='completed'）：这里拿到的订单是分页切片，应用层遍历只会算出偏小的金额。
        BigDecimal revenue = orderService.getShopRevenue(
                shop.getId(), OrderType.PRODUCT.getCode());

        shopOverview.setItemCount(products.getTotal());
        shopOverview.setPendingCount(pendingShipments.getTotal());
        shopOverview.setWarningCount(lowStock.getTotal());
        shopOverview.setRevenue(revenue);
        // 交易数与营业额同口径，只算 completed：混入 paid 或 shipped 会让"成交笔数"高于实际完成量。
        shopOverview.setTransactionCount(orderService.countShopCompletedOrders(
                shop.getId(), OrderType.PRODUCT.getCode()));
        summary.setProductCount(summary.getProductCount() + products.getTotal());
        summary.setPendingShipments(summary.getPendingShipments() + pendingShipments.getTotal());
        summary.setLowStockCount(summary.getLowStockCount() + lowStock.getTotal());
        // 汇总用 BigDecimal.add 逐店累加而不是 double 求和：金额要按分精确，浮点累加会带出分位误差。
        summary.setTotalRevenue(summary.getTotalRevenue().add(revenue));
        summary.setTransactionCount(summary.getTransactionCount()
                + orderService.countShopCompletedOrders(shop.getId(), OrderType.PRODUCT.getCode()));
        recentOrders.addAll(orders.getList());
        lowStockProducts.addAll(lowStock.getList());
    }

    private void collectServiceData(Shop shop,
                                    MerchantDashboardOverview.ShopOverview shopOverview,
                                    MerchantDashboardOverview.Summary summary,
                                    List<Order> todayAppointments) {
        // 服务门店的服务项与商城的商品共表，靠 product_type=service 区分，计数同样只取 total。
        PageResult<Product> services = productService.getMerchantProducts(
                shop.getId(), null, null, null, null,
                ProductType.SERVICE.getCode(), 1, 1);
        // 待服务口径是 confirmed 的预约单：已确认但还没到店完成的场次才需要商家跟进提醒。
        PageResult<Order> pendingAppointments = orderService.getMerchantOrders(
                shop.getId(), OrderStatus.CONFIRMED.getCode(), OrderType.BOOKING.getCode(), null, 1, 1);
        // 待办预约先按店铺取前 100 条再在内存里筛当天：getMerchantOrders 不支持按日期下推，超过 100 条的店铺会漏掉部分今日场次。
        PageResult<Order> appointments = orderService.getMerchantOrders(
                shop.getId(), null, OrderType.BOOKING.getCode(), null, 1, SHOP_DATA_LIMIT);
        // 服务营业额按 orderType=booking 单独聚合：同一商家既有商品又有服务时，两类金额不能混在一起算。
        BigDecimal revenue = orderService.getShopRevenue(
                shop.getId(), OrderType.BOOKING.getCode());

        shopOverview.setItemCount(services.getTotal());
        shopOverview.setPendingCount(pendingAppointments.getTotal());
        shopOverview.setRevenue(revenue);
        shopOverview.setTransactionCount(orderService.countShopCompletedOrders(
                shop.getId(), OrderType.BOOKING.getCode()));
        summary.setServiceCount(summary.getServiceCount() + services.getTotal());
        summary.setPendingAppointments(
                summary.getPendingAppointments() + pendingAppointments.getTotal());
        summary.setTotalRevenue(summary.getTotalRevenue().add(revenue));
        summary.setTransactionCount(summary.getTransactionCount()
                + orderService.countShopCompletedOrders(shop.getId(), OrderType.BOOKING.getCode()));
        for (Order appointment : appointments.getList()) {
            // 只把当天场次放进看板，历史与未来预约留在预约列表页，否则"今日预约"会被无关场次占满。
            if (isToday(appointment)) {
                todayAppointments.add(appointment);
            }
        }
    }

    private MerchantDashboardOverview.ShopOverview toShopOverview(Shop shop) {
        MerchantDashboardOverview.ShopOverview overview =
                new MerchantDashboardOverview.ShopOverview();
        overview.setId(shop.getId());
        overview.setName(shop.getName());
        overview.setType(shop.getType());
        overview.setStatus(shop.getStatus());
        overview.setLogo(shop.getLogo());
        overview.setAddress(shop.getAddress());
        // 未营业门店不进入 collectMallData/collectServiceData，计数字段必须显式归零，否则会以 null 下发。
        overview.setTransactionCount(0);
        overview.setItemCount(0);
        overview.setPendingCount(0);
        overview.setWarningCount(0);
        return overview;
    }

    private boolean isToday(Order appointment) {
        // 以排班时段 slotDate 为准，缺失时才回落到订单上的 appointment_time：两处冗余字段可能只写了其中一个。
        AppointmentSlot slot = firstSlot(appointment);
        if (slot != null && slot.getSlotDate() != null) {
            return LocalDate.now().equals(slot.getSlotDate());
        }
        return appointment.getAppointmentTime() != null
                && LocalDate.now().equals(appointment.getAppointmentTime().toLocalDate());
    }

    private LocalDateTime appointmentSortValue(Order appointment) {
        // 排序键与 isToday 取同一来源，保证"是否今天"和"当天先后"用的是同一条时间线。
        AppointmentSlot slot = firstSlot(appointment);
        if (slot != null && slot.getSlotDate() != null && slot.getStartTime() != null) {
            return LocalDateTime.of(slot.getSlotDate(), slot.getStartTime());
        }
        return appointment.getAppointmentTime();
    }

    private AppointmentSlot firstSlot(Order appointment) {
        if (appointment.getAppointmentSlots() == null
                || appointment.getAppointmentSlots().isEmpty()) {
            return null;
        }
        return appointment.getAppointmentSlots().get(0);
    }

    private <T> List<T> limit(List<T> values, int maximum) {
        // 看板只展示摘要：超出上限直接截断成新列表，不回传全量数据让前端自己截。
        return values.size() <= maximum
                ? values : new ArrayList<>(values.subList(0, maximum));
    }
}
