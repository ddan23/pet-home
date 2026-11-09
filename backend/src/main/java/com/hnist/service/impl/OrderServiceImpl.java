package com.hnist.service.impl;

import com.hnist.common.exception.BusinessException;
import com.hnist.common.constant.OrderStatus;
import com.hnist.common.constant.OrderType;
import com.hnist.common.constant.ProductStatus;
import com.hnist.common.constant.ProductType;
import com.hnist.common.constant.ShopType;
import com.hnist.mapper.CartMapper;
import com.hnist.mapper.AppointmentSlotMapper;
import com.hnist.mapper.OrderAppointmentSlotMapper;
import com.hnist.mapper.OrderMapper;
import com.hnist.mapper.ProductMapper;
import com.hnist.mapper.ShopMapper;
import com.hnist.vo.OrderItemDTO;
import com.hnist.vo.OrderCreationResponse;
import com.hnist.vo.BookingCreationResponse;
import com.hnist.entity.AppointmentSlot;
import com.hnist.entity.CartItem;
import com.hnist.entity.Order;
import com.hnist.common.result.PageResult;
import com.hnist.entity.Product;
import com.hnist.entity.Shop;
import com.hnist.service.OrderService;
import com.hnist.service.AppointmentScheduleService;
import com.hnist.support.OrderNumberGenerator;
import com.hnist.common.util.PageUtils;
import com.hnist.event.NotificationEvent;
import com.hnist.event.NotificationEventPublisher;
import com.hnist.support.OrderStockService;
import com.hnist.support.OrderTimeoutMessageSender;
import com.hnist.support.ShippingAddressValidator;
import com.hnist.policy.OrderStatusTransitionPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
/** 订单从创建、支付、取消到履约的流程实现，含库存扣减与状态通知。 */
@Service
public class OrderServiceImpl implements OrderService {

    // 支付时限只由这一个常量定义：支付拦截与定时回收共用同一口径，否则会出现还能支付却被回收的订单。
    private static final long PAYMENT_TIMEOUT_MINUTES = 10L;
    // 预约提醒窗口固定在开始前 2 小时，同时决定 SQL 查询区间与通知文案里的“2小时内”。
    private static final int APPOINTMENT_REMINDER_MINUTES = 120;
    // 预约提醒的时间计算固定用东八区，服务器跑在 UTC 时不能依赖 JVM 默认时区。
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter APPOINTMENT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final OrderMapper orderMapper;
    private final CartMapper cartMapper;
    private final ProductMapper productMapper;
    private final ShopMapper shopMapper;
    private final NotificationEventPublisher notificationPublisher;
    private final AppointmentSlotMapper appointmentSlotMapper;
    private final OrderAppointmentSlotMapper orderAppointmentSlotMapper;
    private final AppointmentScheduleService appointmentScheduleService;
    private final ObjectMapper objectMapper;
    private final OrderStockService orderStockService;
    private final OrderTimeoutMessageSender orderTimeoutMessageSender;

    public OrderServiceImpl(OrderMapper orderMapper, CartMapper cartMapper, ProductMapper productMapper,
                            ShopMapper shopMapper,
                            NotificationEventPublisher notificationPublisher,
                            AppointmentSlotMapper appointmentSlotMapper,
                            OrderAppointmentSlotMapper orderAppointmentSlotMapper,
                            AppointmentScheduleService appointmentScheduleService,
                            ObjectMapper objectMapper,
                            OrderStockService orderStockService,
                            OrderTimeoutMessageSender orderTimeoutMessageSender) {
        this.orderMapper = orderMapper;
        this.cartMapper = cartMapper;
        this.productMapper = productMapper;
        this.shopMapper = shopMapper;
        this.notificationPublisher = notificationPublisher;
        this.appointmentSlotMapper = appointmentSlotMapper;
        this.orderAppointmentSlotMapper = orderAppointmentSlotMapper;
        this.appointmentScheduleService = appointmentScheduleService;
        this.objectMapper = objectMapper;
        this.orderStockService = orderStockService;
        this.orderTimeoutMessageSender = orderTimeoutMessageSender;
    }

    @Override
    public PageResult<Order> getUserOrders(Integer userId, String status, String orderType, int page, int pageSize) {
        // 过期预约没有独立定时任务：列表查询前先回收，把 confirmed 改成 cancelled 并归还名额，用户不会看到已过期的“已确认”。
        if (OrderType.BOOKING.getCode().equals(orderType)) {
            autoCancelExpiredBookings();
        }
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(pageSize);
        int offset = (safePage - 1) * safePageSize;
        List<Order> list = orderMapper.selectByUserId(
                userId, status, orderType, offset, safePageSize);
        attachAppointmentSlots(list);
        // 总数用不带 LIMIT 的独立统计，保证分页器显示的是全量条数而不是当前页条数。
        int total = orderMapper.countByUserId(userId, status, orderType);
        return new PageResult<>(list, total, safePage, safePageSize);
    }

    // 购物车结算：一个购物车可能装着多家店铺的商品，必须按店铺拆单，每笔订单才能独立支付、发货与归属营收。
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCreationResponse createOrder(Integer userId,
                                             String shippingAddress) {
        // 收货地址以 JSON 快照写入订单，先校验必填项与手机号格式，避免脏地址被固化到历史订单里。
        ShippingAddressValidator.validate(shippingAddress, objectMapper);
        // 下单前先按商品可用性重算勾选状态：已下架/停售的商品取消勾选，不参与本次结算。
        cartMapper.uncheckUnavailableByUserId(userId);
        List<CartItem> checkedItems = selectCheckedItems(cartMapper.selectByUserId(userId));
        if (checkedItems.isEmpty()) {
            throw new BusinessException("所选商品均已下架或暂停销售，请重新选择");
        }

        SettlementContext context = new SettlementContext();
        Map<Integer, List<CartItem>> shopGroup = groupByShop(userId, checkedItems, context);
        if (shopGroup.isEmpty()) {
            throw new BusinessException("所选商品均已下架或暂停销售，请重新选择");
        }

        List<Integer> orderIds = createShopOrders(userId, shippingAddress, shopGroup, context);
        if (orderIds.isEmpty()) {
            throw new BusinessException("没有可结算的商品");
        }
        // 只删除本次成功下单的购物车行；未勾选与已失效的项保留，避免用户回头找不到自己的购物车内容。
        if (!context.cartItemIds.isEmpty()) {
            cartMapper.batchDelete(context.cartItemIds);
        }
        // 事务提交后为每笔订单投递支付超时消息（TTL+DLX 延迟关单）；发送失败仅记日志，兜底轮询保证最终回收。
        for (Integer orderId : orderIds) {
            orderTimeoutMessageSender.sendAfterCommit(orderId);
        }
        // 返回多笔订单 id 与被跳过的商品名，前端据此提示拆单结果和失效项。
        return OrderCreationResponse.batch(orderIds, context.skippedItemNames);
    }

    /** 只保留用户勾选的行：未勾选项继续留在购物车，不进入拆单与扣库存流程。 */
    private List<CartItem> selectCheckedItems(List<CartItem> cartItems) {
        List<CartItem> checkedItems = new ArrayList<>(cartItems.size());
        for (CartItem item : cartItems) {
            if (Boolean.TRUE.equals(item.getChecked())) {
                checkedItems.add(item);
            }
        }
        return checkedItems;
    }

    /**
     * 按店铺分组并缓存最新商品：失效商品回写为未勾选，库存不足直接终止整单。
     * 用 LinkedHashMap 保持店铺首次出现顺序，拆出的多笔订单与购物车展示顺序一致。
     */
    private Map<Integer, List<CartItem>> groupByShop(Integer userId, List<CartItem> checkedItems,
                                                     SettlementContext context) {
        Map<Integer, List<CartItem>> shopGroup = new LinkedHashMap<>();
        List<Integer> invalidCartItemIds = new ArrayList<>();
        for (CartItem item : checkedItems) {
            // 购物车行的商品信息可能已过期，逐项回查最新商品，只结算仍在上架中的商品。
            Product product = productMapper.selectPublicById(
                    item.getProductId(), ProductType.GOODS.getCode());
            if (product == null
                    || !ProductStatus.ON.getCode().equals(product.getStatus())
                    || !ProductType.GOODS.getCode().equals(product.getProductType())) {
                invalidCartItemIds.add(item.getId());
                context.skippedItemNames.add(item.getProductName());
                continue;
            }
            // 库存判断只为给出明确提示，最终能否扣到由 tryReserve 的条件更新裁决，不能作为并发依据。
            if (product.getStock() != null && product.getStock() < item.getQuantity()) {
                throw new BusinessException(
                        "商品[" + product.getName() + "]库存不足，当前库存：" + product.getStock());
            }
            context.productCache.put(product.getId(), product);
            shopGroup.computeIfAbsent(product.getShopId(), key -> new ArrayList<>()).add(item);
        }
        if (!invalidCartItemIds.isEmpty()) {
            cartMapper.updateCheckedByIds(userId, invalidCartItemIds, false);
        }
        return shopGroup;
    }

    /** 逐店铺建单：任一店铺扣库存失败都会抛异常，整批订单与已占库存一起回滚。 */
    private List<Integer> createShopOrders(Integer userId, String shippingAddress,
                                           Map<Integer, List<CartItem>> shopGroup,
                                           SettlementContext context) {
        List<Integer> orderIds = new ArrayList<>(shopGroup.size());
        for (Map.Entry<Integer, List<CartItem>> entry : shopGroup.entrySet()) {
            Order order = buildShopOrder(
                    userId, shippingAddress, entry.getKey(), entry.getValue(), context);
            orderMapper.insert(order);
            orderIds.add(order.getId());
        }
        return orderIds;
    }

    /** 单店铺订单：金额按数据库最新商品价重算，库存用条件更新扣减，明细落成快照。 */
    private Order buildShopOrder(Integer userId, String shippingAddress, Integer shopId,
                                 List<CartItem> shopItems, SettlementContext context) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItemDTO> itemsJson = new ArrayList<>(shopItems.size());
        for (CartItem cartItem : shopItems) {
            Product product = context.productCache.get(cartItem.getProductId());
            if (product == null) {
                throw new BusinessException("商品信息已变化，请刷新购物车后重试");
            }
            totalAmount = totalAmount.add(
                    product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
            itemsJson.add(new OrderItemDTO(product.getId(), product.getName(),
                    product.getPrice(), cartItem.getQuantity()));
            context.cartItemIds.add(cartItem.getId());
            // 库存必须用条件更新扣减：WHERE 带 stock >= quantity，并发下只有一个请求成功，影响行数为 0 就回滚整单。
            if (!orderStockService.tryReserve(product.getId(), cartItem.getQuantity())) {
                throw new BusinessException("商品[" + product.getName() + "]库存不足");
            }
        }

        Order order = new Order();
        order.setOrderNo(OrderNumberGenerator.next());
        order.setUserId(userId);
        order.setShopId(shopId);
        order.setOrderType(OrderType.PRODUCT.getCode());
        order.setTotalAmount(totalAmount);
        // 新单一律进入 pending_payment，10 分钟内未支付会被 cancelExpiredPendingOrders 回收并释放库存。
        order.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
        // 地址与明细以 JSON 快照落库：商品后续改价改名，历史订单仍按成交时的内容展示。
        order.setShippingAddress(writeJson(shippingAddress));
        order.setItems(writeJson(itemsJson));
        return order;
    }

    /** 一次结算跨步骤共享的中间结果，避免为传参把方法签名撑到十几个参数。 */
    private static final class SettlementContext {
        private final Map<Integer, Product> productCache = new HashMap<>();
        private final List<Integer> cartItemIds = new ArrayList<>();
        private final List<String> skippedItemNames = new ArrayList<>();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCreationResponse directPurchase(Integer userId, Integer productId,
                                                Integer quantity,
                                                String shippingAddress) {
        // 直接购买没有购物车做数量兜底，数量缺失或小于 1 只能在这里拦下。
        if (quantity == null || quantity < 1) {
            throw new BusinessException("商品数量不能小于1");
        }
        ShippingAddressValidator.validate(shippingAddress, objectMapper);
        Product product = productMapper.selectPublicById(
                productId, ProductType.GOODS.getCode());
        // 商品不存在、已下架、类型不是 goods 合并为 404，对外只表达“该商品不可购买”。
        if (product == null
                || !ProductStatus.ON.getCode().equals(product.getStatus())
                || !ProductType.GOODS.getCode().equals(product.getProductType())) {
            throw new BusinessException(404, "商品不存在或已下架");
        }
        if (product.getStock() != null && product.getStock() < quantity) {
            throw new BusinessException("库存不足");
        }

        BigDecimal totalAmount = product.getPrice().multiply(BigDecimal.valueOf(quantity));

        List<OrderItemDTO> itemsJson = new ArrayList<>();
        OrderItemDTO itemDTO = new OrderItemDTO(
                product.getId(), product.getName(), product.getPrice(), quantity);
        itemsJson.add(itemDTO);

        // 先占库存再写订单：插入失败时同一事务回滚，不会留下扣了库存却没有订单的脏数据。
        if (!orderStockService.tryReserve(product.getId(), quantity)) {
            throw new BusinessException("库存不足");
        }

        Order order = new Order();
        order.setOrderNo(OrderNumberGenerator.next());
        order.setUserId(userId);
        order.setShopId(product.getShopId());
        order.setOrderType(OrderType.PRODUCT.getCode());
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
        order.setShippingAddress(writeJson(shippingAddress != null ? shippingAddress : ""));
        order.setItems(writeJson(itemsJson));
        orderMapper.insert(order);
        // 事务提交后投递支付超时消息（TTL+DLX 延迟关单），与购物车结算链路共用同一发送器。
        orderTimeoutMessageSender.sendAfterCommit(order.getId());
        return OrderCreationResponse.single(order.getId());
    }

    // 预约下单的库存是时段名额，占用与订单落库都在 AppointmentScheduleServiceImpl 的事务内完成，这里只保留统一的订单入口。
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingCreationResponse createBooking(Integer userId, Integer serviceId,
                                                 Integer petId, List<Integer> slotIds,
                                                 String note) {
        return appointmentScheduleService.createBooking(
                userId, serviceId, petId, slotIds, note);
    }

    // 支付是商品订单状态机的唯一入口：只有 pending_payment 且在支付时限内的订单能进入 paid。
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Integer orderId, Integer userId) {
        Order order = orderMapper.selectById(orderId);
        // 归属校验用 userId 判定：他人的订单一律返回 404 而不是 403，避免暴露订单是否存在。
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(404, "订单不存在");
        }
        // 可支付状态由 OrderStatusTransitionPolicy 统一判定，防止各入口写出不同的状态白名单。
        if (!OrderStatusTransitionPolicy.isPayable(order.getStatus())) {
            throw new BusinessException("当前状态不可支付");
        }
        // 支付时限以 createdAt 为起点：超时订单即使仍是 pending_payment 也直接拒绝，等定时任务回收库存。
        if (isPaymentExpired(order)) {
            throw new BusinessException("订单已超过" + PAYMENT_TIMEOUT_MINUTES
                    + "分钟支付时限，请刷新订单状态");
        }
        // 条件更新抢占 pending_payment → paid：影响行数为 0 说明并发支付或定时回收已改写状态，必须抛错，否则会重复发通知。
        if (orderMapper.updateStatusIfCurrent(
                orderId, OrderStatus.PENDING_PAYMENT.getCode(),
                OrderStatus.PAID.getCode()) == 0) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }

        // 用户支付通知用 order-user-paid:{订单id} 作幂等键：重复投递只会落一条记录。
        notificationPublisher.publish(new NotificationEvent(
                order.getUserId(), null, ShopType.MALL.getCode(),
                OrderStatus.PAID.getCode(), "info",
                "支付成功",
                "订单号：" + order.getOrderNo()
                        + "\n支付金额：¥" + order.getTotalAmount()
                        + "\n商家正在准备发货。",
                "order", order.getId().longValue(), "/me",
                "order-user-paid:" + order.getId()));

        // 商家通知是旁路：用户已付款的事实已经提交，这里任何异常只记日志，不能让通知问题回滚支付结果。
        try {
            Shop shop = shopMapper.selectById(order.getShopId());
            if (shop != null && shop.getOwnerId() != null) {
                String itemsDesc = "";
                try {
                    // items 是下单时的 JSON 快照，解析失败只降级为“查看详情”，不能让通知整体失败。
                    List<OrderItemDTO> itemList = objectMapper.readValue(order.getItems(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, OrderItemDTO.class));
                    itemsDesc = itemList.stream()
                        .map(it -> it.getName() + " ×" + it.getQuantity())
                        .reduce((a, b) -> a + "、" + b).orElse("");
                } catch (JsonProcessingException exception) {
                    log.warn("订单商品明细解析失败，订单号：{}", order.getOrderNo(), exception);
                }
                String content = "订单号：" + order.getOrderNo()
                    + "\n商品：" + (itemsDesc.isEmpty() ? "查看详情" : itemsDesc)
                    + "\n金额：¥" + order.getTotalAmount()
                    + "\n收货地址：" + order.getShippingAddress();
                // order-paid:{订单id} 与用户侧幂等键分开：同一笔支付只允许商家收到一条待发货提醒。
                notificationPublisher.publish(new NotificationEvent(
                        shop.getOwnerId(), userId, ShopType.MALL.getCode(),
                        "new_order", "info",
                        "您有新的订单，请及时发货", content, "order", order.getId().longValue(),
                        "/merchant/mall", "order-paid:" + order.getId()));
            }
        } catch (RuntimeException exception) {
            log.warn("订单支付成功，但商家通知发送失败，订单号：{}", order.getOrderNo(), exception);
        }

    }

    // 取消要把状态变更和资源归还放在同一事务：商品订单回补库存，预约订单归还时段名额。
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Integer orderId, Integer userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(404, "订单不存在");
        }

        if (OrderType.BOOKING.getCode().equals(order.getOrderType())) {
            // 预约订单走独立的状态机：只有 confirmed 可取消，其他状态说明已完成或已取消，不可重复操作。
            if (!OrderStatusTransitionPolicy.isBookingCancellable(order.getStatus())) {
                throw new BusinessException("当前状态不可取消");
            }
            // 预约开始后不再由用户取消，避免与 autoCancelExpiredBookings 的超时取消同时改写同一笔预约。
            if (order.getAppointmentTime() != null && order.getAppointmentTime().isBefore(LocalDateTime.now())) {
                throw new BusinessException("预约时间已过，不可取消");
            }
            if (orderMapper.updateStatusIfCurrent(orderId,
                    OrderStatus.CONFIRMED.getCode(),
                    OrderStatus.CANCELLED.getCode()) == 0) {
                throw new BusinessException("预约状态已变化，请刷新后重试");
            }
            // 取消成功必须归还 booked_count：名额只能靠 releaseCapacityByOrderId 回减，漏放会让该时段永久少卖一个位置。
            orderAppointmentSlotMapper.releaseCapacityByOrderId(
                    orderId, order.getShopId());

            // 通知失败只记日志：取消已生效，回滚会让订单状态与时段名额重新错位。
            try {
                Shop shop = shopMapper.selectById(order.getShopId());
                if (shop != null && shop.getOwnerId() != null) {
                    String content = "预约号：" + order.getOrderNo()
                        + "\n预约时段：" + formatBookedSlots(orderId)
                        + "\n金额：¥" + order.getTotalAmount()
                        + "\n该预约已被用户取消。";
                    // booking-user-cancelled:{订单id} 保证一次取消只给商家留一条记录。
                    notificationPublisher.publish(new NotificationEvent(
                            shop.getOwnerId(), userId, ShopType.SERVICE.getCode(),
                            "cancelled", "warning",
                            "用户取消了预约", content, "order", order.getId().longValue(),
                            "/merchant/services", "booking-user-cancelled:" + order.getId()));
                }
            } catch (RuntimeException exception) {
                log.warn("预约取消成功，但商家通知发送失败，订单号：{}", order.getOrderNo(), exception);
            }
        } else {
            // 商品订单只有 pending_payment 可由用户取消：支付完成后没有退款流程，只能继续履约。
            if (!OrderStatusTransitionPolicy.isProductCancellable(order.getStatus())) {
                throw new BusinessException("当前状态不可取消");
            }
            // 条件更新同时充当并发闸门：抢到 pending_payment → cancelled 的请求才继续回补库存。
            if (orderMapper.updateStatusIfCurrent(
                    orderId, OrderStatus.PENDING_PAYMENT.getCode(),
                    OrderStatus.CANCELLED.getCode()) == 0) {
                throw new BusinessException("订单状态已变化，请刷新后重试");
            }
            // 库存按下单时的 items 快照逐项回补，与下单时的扣减一一对应。
            orderStockService.restore(order);

            // 通知失败只记日志：库存已归还，不能因为通知异常把已生效的取消回滚掉。
            try {
                Shop shop = shopMapper.selectById(order.getShopId());
                if (shop != null && shop.getOwnerId() != null) {
                    String content = "订单号：" + order.getOrderNo()
                        + "\n商品：" + order.getItems()
                        + "\n金额：¥" + order.getTotalAmount()
                        + "\n该订单已被用户取消。";
                    notificationPublisher.publish(new NotificationEvent(
                            shop.getOwnerId(), userId, ShopType.MALL.getCode(),
                            "cancelled", "warning",
                            "用户取消了订单", content, "order", order.getId().longValue(),
                            "/merchant/mall", "order-user-cancelled:" + order.getId()));
                }
            } catch (RuntimeException exception) {
                log.warn("订单取消成功，但商家通知发送失败，订单号：{}", order.getOrderNo(), exception);
            }
        }

    }

    // 提醒发货不落业务表，重复点击靠幂等键收敛：同一订单同一天只产生一条商家通知。
    @Override
    public void remindShipment(Integer orderId, Integer userId) {
        Order order = orderMapper.selectById(orderId);
        // 同时校验归属和订单类型：预约订单没有发货概念，一并按 404 处理，不暴露订单的真实归属。
        if (order == null || !order.getUserId().equals(userId)
                || !OrderType.PRODUCT.getCode().equals(order.getOrderType())) {
            throw new BusinessException(404, "订单不存在");
        }
        // 只有 paid（待发货）状态可提醒：已发货或已完成再提醒只会打扰商家。
        if (!OrderStatus.PAID.getCode().equals(order.getStatus())) {
            throw new BusinessException("只有待发货订单可以提醒发货");
        }
        Shop shop = shopMapper.selectById(order.getShopId());
        if (shop == null || shop.getOwnerId() == null) {
            throw new BusinessException(404, "商家信息不存在");
        }
        String content = "订单号：" + order.getOrderNo()
                + "\n用户已提醒您尽快处理发货。"
                + "\n商品：" + order.getItems()
                + "\n金额：¥" + order.getTotalAmount();
        // 幂等键带当天日期，配合 uk_notification_dedupe(user_id, dedupe_key)：当天重复提醒只会写入一条。
        notificationPublisher.publish(new NotificationEvent(
                shop.getOwnerId(), userId, ShopType.MALL.getCode(),
                "remind_shipment", "info",
                "用户提醒您发货", content, "order", order.getId().longValue(),
                "/merchant/mall",
                "order-remind-shipment:" + order.getId() + ":" + LocalDate.now()));
    }

    // 确认收货收口在 shipped → completed，用户无法跳过发货直接把订单置为完成。
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceived(Integer orderId, Integer userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(404, "订单不存在");
        }
        if (!OrderStatusTransitionPolicy.isReceivable(order.getStatus())) {
            throw new BusinessException("当前状态不可确认收货");
        }
        // 影响行数为 0 说明状态已被并发请求改写，抛错让前端刷新，避免基于过期快照继续后续操作。
        if (orderMapper.updateStatusIfCurrent(
                orderId, OrderStatus.SHIPPED.getCode(),
                OrderStatus.COMPLETED.getCode()) == 0) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }
        Shop shop = shopMapper.selectById(order.getShopId());
        // 这里不包 try/catch：通知事件由 NotificationEventListener 在事务提交后写入并自行吞掉异常，不影响已完成的订单。
        if (shop != null && shop.getOwnerId() != null) {
            notificationPublisher.publish(new NotificationEvent(
                    shop.getOwnerId(), userId, ShopType.MALL.getCode(),
                    OrderStatus.COMPLETED.getCode(), "info",
                    "订单已完成",
                    "订单号：" + order.getOrderNo()
                            + "\n用户已确认收货，订单交易完成。"
                            + "\n订单金额：¥" + order.getTotalAmount(),
                    "order", order.getId().longValue(), "/merchant/mall",
                    "order-completed:" + order.getId()));
        }
    }

    // 商家端列表按 shopId 隔离：shopId 由 Controller 从登录态推出，SQL 以 o.shop_id 过滤，商家看不到别家订单。
    @Override
    public PageResult<Order> getMerchantOrders(Integer shopId, String status, String orderType,
                                               String keyword, int page, int pageSize) {
        // 查询预约类型时先做一次过期回收，保证商家看到的 confirmed 都是真正待服务的预约。
        if (OrderType.BOOKING.getCode().equals(orderType)) {
            autoCancelExpiredBookings();
        }
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(pageSize);
        int offset = (safePage - 1) * safePageSize;
        List<Order> list = orderMapper.selectByShopId(
                shopId, status, orderType, keyword, offset, safePageSize);
        attachAppointmentSlots(list);
        int total = orderMapper.countByShopId(shopId, status, orderType, keyword);
        return new PageResult<>(list, total, safePage, safePageSize);
    }

    // 总部视角按 ownerId 关联 shops 过滤：shopId 与 customerId 为空表示不限门店、不限客户。
    @Override
    public PageResult<Order> getMerchantOrdersByOwner(Integer ownerId, Integer shopId,
                                                       String status, String orderType,
                                                       int page, int pageSize, Integer customerId) {
        if (OrderType.BOOKING.getCode().equals(orderType)) {
            autoCancelExpiredBookings();
        }
        int safePage = PageUtils.normalizePage(page);
        int safePageSize = PageUtils.normalizePageSize(pageSize);
        int offset = (safePage - 1) * safePageSize;
        List<Order> list = orderMapper.selectByOwner(
                ownerId, shopId, customerId, status, orderType, offset, safePageSize);
        attachAppointmentSlots(list);
        int total = orderMapper.countByOwner(ownerId, shopId, customerId, status, orderType);
        return new PageResult<>(list, total, safePage, safePageSize);
    }

    // 发货只允许 paid → shipped，且订单必须属于当前店铺。
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void shipOrder(Integer orderId, Integer shopId) {
        Order order = orderMapper.selectById(orderId);
        // shopId 不符与订单不存在同样返回 404，避免商家探测其他店铺的订单号。
        if (order == null || !order.getShopId().equals(shopId)) {
            throw new BusinessException(404, "订单不存在");
        }
        // 未支付或已发货的订单不能重复发货，状态白名单来自 OrderStatusTransitionPolicy。
        if (!OrderStatusTransitionPolicy.isShippable(order.getStatus())) {
            throw new BusinessException("当前状态不可发货");
        }
        // 并发发货时只有抢到 paid 的那次更新成功，影响行数为 0 说明状态已被改写，直接失败。
        if (orderMapper.updateStatusIfCurrent(
                orderId, OrderStatus.PAID.getCode(),
                OrderStatus.SHIPPED.getCode()) == 0) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }

        // 通知失败只记日志：货已经发出，回滚状态会让用户与商家看到相互矛盾的进展。
        try {
            Shop shop = shopMapper.selectById(order.getShopId());
            String shopName = shop != null ? shop.getName() : "商家";
            String content = "订单号：" + order.getOrderNo()
                + "\n您的订单已由「" + shopName + "」发货，请留意收货。"
                + "\n金额：¥" + order.getTotalAmount();
            if (shop != null) {
                notificationPublisher.publish(new NotificationEvent(
                        order.getUserId(), shop.getOwnerId(), ShopType.MALL.getCode(),
                        "shipped", "info",
                        "订单已发货", content, "order", order.getId().longValue(),
                        "/me", "order-shipped:" + order.getId()));
            }
        } catch (RuntimeException exception) {
            log.warn("订单已发货，但用户通知发送失败，订单号：{}", order.getOrderNo(), exception);
        }

    }

    // 商家处理预约结果：只能在 confirmed 上置为 completed 或 cancelled。
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAppointmentStatus(Integer orderId, Integer shopId,
                                        Integer operatorId, String status) {
        Order order = orderMapper.selectById(orderId);
        // shopId 归属、订单类型两道校验都由这里兜底：Controller 已经验过门店归属，这里防止内部调用绕过。
        if (order == null || !shopId.equals(order.getShopId())
                || !OrderType.BOOKING.getCode().equals(order.getOrderType())) {
            throw new BusinessException(404, "预约不存在");
        }
        // 商家只能把预约写成终态：只接受 completed 或 cancelled，其余状态值不由此接口维护。
        if (!OrderStatusTransitionPolicy.isAppointmentResultStatus(status)) {
            throw new BusinessException("预约状态不合法");
        }
        if (!OrderStatusTransitionPolicy.isBookingCancellable(order.getStatus())) {
            throw new BusinessException("当前状态不可操作");
        }
        // 状态更新与名额归还要么都生效要么都回滚，避免出现已取消但仍占着名额的预约。
        if (orderMapper.updateStatusIfCurrent(
                orderId, OrderStatus.CONFIRMED.getCode(), status) == 0) {
            throw new BusinessException("预约状态已变化，请刷新后重试");
        }
        // 只有取消需要回减 booked_count；完成代表服务已消耗，名额不应再放回。
        if (OrderStatus.CANCELLED.getCode().equals(status)) {
            orderAppointmentSlotMapper.releaseCapacityByOrderId(orderId, shopId);
        }
        String title = OrderStatus.COMPLETED.getCode().equals(status)
                ? "服务预约已完成" : "服务预约已被取消";
        String action = OrderStatus.COMPLETED.getCode().equals(status)
                ? "已完成" : "已被商家取消";
        String content = "预约号：" + order.getOrderNo()
                + "\n您的服务预约" + action + "。"
                + "\n预约时段：" + formatBookedSlots(orderId)
                + "\n如有疑问请联系商家客服。";
        // booking-{状态}:{订单id} 作幂等键：同一预约的同一结果只留一条通知，重复提交不会刷屏。
        notificationPublisher.publish(new NotificationEvent(
                order.getUserId(), operatorId, "service", status,
                OrderStatus.CANCELLED.getCode().equals(status) ? "warning" : "info",
                title, content, "order", order.getId().longValue(), "/me",
                "booking-" + status + ":" + order.getId()));
    }

    // 回收所有时段已结束但仍为 confirmed 的预约：逐条条件改状态后再归还 booked_count，保证每笔只回收一次。
    private void autoCancelExpiredBookings() {
        List<Order> expiredList = orderMapper.selectExpiredBookings();
        if (expiredList == null || expiredList.isEmpty()) {
            return;
        }
        for (Order order : expiredList) {
            // 必须条件更新而非直接 update：返回 0 说明用户已取消或商家已处理，跳过才能避免名额被重复归还两次。
            if (orderMapper.updateStatusIfCurrent(
                    order.getId(), OrderStatus.CONFIRMED.getCode(),
                    OrderStatus.CANCELLED.getCode()) == 0) {
                continue;
            }
            // 先改状态成功再释放名额，顺序不能反：先放名额后改状态失败会留下多放的容量。
            orderAppointmentSlotMapper.releaseCapacityByOrderId(
                    order.getId(), order.getShopId());
            try {
                // 用户与商家各一条通知，幂等键分别为 booking-expired-user / booking-expired-merchant，重复扫描不会重复发送。
                String userContent = "预约号：" + order.getOrderNo()
                    + "\n预约时段：" + formatBookedSlots(order.getId())
                    + "\n您的服务预约已超时，系统已自动取消。如需服务请重新预约。";
                notificationPublisher.publish(new NotificationEvent(
                        order.getUserId(), null, ShopType.SERVICE.getCode(),
                        "cancelled", "warning",
                        "预约超时自动取消", userContent, "order", order.getId().longValue(),
                        "/me", "booking-expired-user:" + order.getId()));
                Shop shop = shopMapper.selectById(order.getShopId());
                if (shop != null && shop.getOwnerId() != null) {
                    String merchantContent = "预约号：" + order.getOrderNo()
                        + "\n用户：" + (order.getUserName() != null ? order.getUserName() : "用户")
                        + "\n预约时段：" + formatBookedSlots(order.getId())
                        + "\n该预约已超时，系统已自动取消。";
                    notificationPublisher.publish(new NotificationEvent(
                            shop.getOwnerId(), null, ShopType.SERVICE.getCode(),
                            "cancelled", "warning",
                            "预约超时自动取消", merchantContent, "order", order.getId().longValue(),
                            "/merchant/services", "booking-expired-merchant:" + order.getId()));
                }
            } catch (RuntimeException exception) {
                log.warn("预约自动取消，但通知发送失败，订单号：{}", order.getOrderNo(), exception);
            }
        }
    }

    // 定时兜底任务调用（已降频为 5 分钟）：批量回收超时未支付的商品订单，覆盖 MQ 链路故障与消息丢失。
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelExpiredPendingOrders() {
        // 超时口径与 payOrder 一致：都用 createdAt + PAYMENT_TIMEOUT_MINUTES，SQL 端再限定 product 与 pending_payment。
        LocalDateTime createdBefore = LocalDateTime.now()
                .minusMinutes(PAYMENT_TIMEOUT_MINUTES);
        List<Order> expiredOrders =
                orderMapper.selectExpiredPendingProductOrders(createdBefore);
        if (expiredOrders == null || expiredOrders.isEmpty()) {
            return 0;
        }
        int cancelledCount = 0;
        for (Order order : expiredOrders) {
            if (cancelOne(order)) {
                cancelledCount++;
            }
        }
        return cancelledCount;
    }

    // MQ 关单消费者调用：按 id 取消超时未支付的商品订单，预约订单由预约模块自行管理，直接忽略。
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelExpiredPendingOrder(Integer orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !OrderType.PRODUCT.getCode().equals(order.getOrderType())) {
            return;
        }
        cancelOne(order);
    }

    /**
     * 单笔超时取消：条件更新抢占 pending_payment → cancelled，影响行数为 0 说明用户刚支付
     * 或已被其他消费者处理，属正常竞争直接跳过；只有抢到状态更新才回补库存并发通知，
     * 保证一次扣减对应一次释放，重复消费、定时兜底与用户取消并发都不会多放库存。
     */
    private boolean cancelOne(Order order) {
        if (orderMapper.updateStatusIfCurrent(
                order.getId(), OrderStatus.PENDING_PAYMENT.getCode(),
                OrderStatus.CANCELLED.getCode()) == 0) {
            return false;
        }
        orderStockService.restore(order);
        publishOrderTimeoutNotification(order);
        return true;
    }

    // 扫描两小时内即将开始的 confirmed 预约并推送提醒，任务重复执行由幂等键去重。
    @Override
    public void remindUpcomingAppointments() {
        // 提醒区间用东八区时间计算，避免容器时区为 UTC 时把整个提醒窗口偏移。
        LocalDateTime now = LocalDateTime.now(ZONE_ID);
        List<Order> appointments = orderMapper.selectStartingBetween(
                now, now.plusMinutes(APPOINTMENT_REMINDER_MINUTES));
        for (Order order : appointments) {
            publishAppointmentStartReminder(order);
        }
    }

    // 超时取消通知：失败只记日志，订单状态与库存已经落定，通知丢失不影响业务结果。
    private void publishOrderTimeoutNotification(Order order) {
        try {
            String content = "订单号：" + order.getOrderNo()
                    + "\n您的订单超过" + PAYMENT_TIMEOUT_MINUTES
                    + "分钟未支付，系统已自动取消，库存已释放。"
                    + "\n金额：¥" + order.getTotalAmount();
            // order-timeout-cancelled:{订单id} 让任务重跑不会给用户重复发同一条超时通知。
            notificationPublisher.publish(new NotificationEvent(
                    order.getUserId(), null, ShopType.MALL.getCode(),
                    "cancelled", "warning",
                    "订单超时已取消", content, "order",
                    order.getId().longValue(), "/me",
                    "order-timeout-cancelled:" + order.getId()));
        } catch (RuntimeException exception) {
            log.warn("订单超时取消成功，但用户通知发送失败，订单号：{}",
                    order.getOrderNo(), exception);
        }
    }

    private void publishAppointmentStartReminder(Order order) {
        // appointment_pet_name 是下单时的快照，历史预约可能没有宠物名，文案降级而不是中断提醒。
        String petName = order.getAppointmentPetName() == null
                || order.getAppointmentPetName().isBlank()
                ? "未记录" : order.getAppointmentPetName();
        String appointmentTime = APPOINTMENT_TIME_FORMATTER.format(
                order.getAppointmentTime());
        String userContent = "预约号：" + order.getOrderNo()
                + "\n服务门店：" + order.getShopName()
                + "\n宠物：" + petName
                + "\n开始时间：" + appointmentTime
                + "\n您的预约将在2小时内开始，请提前安排。";
        // 用户与商家两条提醒各用独立幂等键（booking-start-user / booking-start-merchant），任务在窗口期内被反复扫描也只会各留一条。
        notificationPublisher.publish(new NotificationEvent(
                order.getUserId(), null, ShopType.SERVICE.getCode(),
                "start_reminder", "info", "预约即将开始", userContent,
                "order", order.getId().longValue(),
                "/me?tab=appointments&status=confirmed",
                "booking-start-user:" + order.getId()));

        Shop shop = shopMapper.selectById(order.getShopId());
        // 门店已归档或没有负责人时跳过商家提醒，用户提醒已发出，不因此回滚或报错。
        if (shop == null || shop.getOwnerId() == null) {
            return;
        }
        String merchantContent = "预约号：" + order.getOrderNo()
                + "\n顾客：" + order.getUserName()
                + "\n宠物：" + petName
                + "\n开始时间：" + appointmentTime
                + "\n该预约将在2小时内开始。";
        notificationPublisher.publish(new NotificationEvent(
                shop.getOwnerId(), order.getUserId(), ShopType.SERVICE.getCode(),
                "start_reminder", "info", "预约即将开始", merchantContent,
                "order", order.getId().longValue(),
                "/merchant/services?tab=appointments",
                "booking-start-merchant:" + order.getId()));
    }

    // 按 id 直查，不做归属校验：调用方（如商家处理预约前的 requireOwnedAppointment）必须自行确认归属。
    @Override
    public Order getOrderById(Integer orderId) {
        return orderMapper.selectById(orderId);
    }

    // 营业额只统计 completed：取消与待支付订单不计入商家收入，SQL 已按 status = 'completed' 过滤。
    @Override
    public BigDecimal getShopRevenue(Integer shopId, String orderType) {
        BigDecimal revenue = orderMapper.getShopRevenue(shopId, orderType);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    // 按 ownerId 关联 shops 汇总名下门店营业额，shopId 为空表示全部门店。
    @Override
    public BigDecimal getMerchantRevenue(Integer ownerId, Integer shopId, String orderType) {
        BigDecimal revenue = orderMapper.getOwnerRevenue(ownerId, shopId, orderType);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    @Override
    public int countShopCompletedOrders(Integer shopId, String orderType) {
        return orderMapper.countShopCompletedOrders(shopId, orderType);
    }

    @Override
    public int countMerchantCompletedOrders(Integer ownerId, Integer shopId, String orderType) {
        return orderMapper.countOwnerCompletedOrders(ownerId, shopId, orderType);
    }

    // 订单里的地址和明细都是 JSON 快照，序列化失败就不该落库：宁可下单失败，也不要写入无法解析的订单。
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "订单数据序列化失败");
        }
    }

    // createdAt 为空按已超时处理：缺时间戳就无法证明订单在支付时限内，不能放行支付。
    private boolean isPaymentExpired(Order order) {
        return order.getCreatedAt() == null
                || !order.getCreatedAt().plusMinutes(PAYMENT_TIMEOUT_MINUTES)
                .isAfter(LocalDateTime.now());
    }

    private void attachAppointmentSlots(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        List<Integer> orderIds = orders.stream()
                .map(Order::getId)
                .toList();
        // 按当前页订单 id 一次查完再回填，避免逐单查询造成 N+1。
        List<AppointmentSlot> slots = appointmentSlotMapper.selectByOrderIds(orderIds);
        if (slots == null || slots.isEmpty()) {
            return;
        }
        Map<Integer, List<AppointmentSlot>> slotsByOrder = new HashMap<>();
        for (AppointmentSlot slot : slots) {
            slotsByOrder.computeIfAbsent(slot.getOrderId(), key -> new ArrayList<>())
                    .add(slot);
        }
        for (Order order : orders) {
            order.setAppointmentSlots(
                    slotsByOrder.getOrDefault(order.getId(), List.of()));
        }
    }

    // 预约时段用于通知文案，关联缺失时返回兜底文案，不能因为查不到时段让整条通知失败。
    private String formatBookedSlots(Integer orderId) {
        List<AppointmentSlot> slots =
                appointmentSlotMapper.selectByOrderIds(List.of(orderId));
        if (slots == null || slots.isEmpty()) {
            return "历史预约时间以订单记录为准";
        }
        return slots.stream()
                .map(slot -> slot.getSlotDate() + " "
                        + slot.getStartTime() + "-" + slot.getEndTime())
                .reduce((left, right) -> left + "；" + right)
                .orElse("历史预约时间以订单记录为准");
    }
}
