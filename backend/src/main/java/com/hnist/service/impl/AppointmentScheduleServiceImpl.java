package com.hnist.service.impl;

import com.hnist.policy.AppointmentSlotPolicy;
import com.hnist.policy.AppointmentSchedulePolicy;
import com.hnist.common.constant.AppointmentSlotStatus;
import com.hnist.common.constant.OrderStatus;
import com.hnist.common.constant.OrderType;
import com.hnist.common.constant.ProductStatus;
import com.hnist.common.constant.ProductType;
import com.hnist.common.constant.ShopType;
import com.hnist.common.exception.BusinessException;
import com.hnist.dto.SaveAppointmentScheduleRequest;
import com.hnist.vo.BookingCreationResponse;
import com.hnist.vo.OrderItemDTO;
import com.hnist.mapper.AppointmentScheduleMapper;
import com.hnist.mapper.AppointmentSlotMapper;
import com.hnist.mapper.OrderAppointmentSlotMapper;
import com.hnist.mapper.OrderMapper;
import com.hnist.mapper.PetMapper;
import com.hnist.mapper.ProductMapper;
import com.hnist.mapper.ShopMapper;
import com.hnist.entity.AppointmentSchedule;
import com.hnist.entity.AppointmentSlot;
import com.hnist.entity.Order;
import com.hnist.entity.OrderAppointmentSlot;
import com.hnist.entity.Pet;
import com.hnist.entity.Product;
import com.hnist.entity.Shop;
import com.hnist.event.NotificationEvent;
import com.hnist.event.NotificationEventPublisher;
import com.hnist.service.AppointmentScheduleService;
import com.hnist.service.ShopService;
import com.hnist.support.OrderNumberGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 预约排班模板生成、时段容量维护与预约下单实现。 */
@Slf4j
@Service
public class AppointmentScheduleServiceImpl implements AppointmentScheduleService {

    private static final DateTimeFormatter SLOT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    private static final String DAY_OPEN = "OPEN";
    private static final String DAY_CLOSED = "CLOSED";

    private final AppointmentScheduleMapper scheduleMapper;
    private final AppointmentSlotMapper slotMapper;
    private final OrderAppointmentSlotMapper orderSlotMapper;
    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final PetMapper petMapper;
    private final ShopMapper shopMapper;
    private final ShopService shopService;
    private final NotificationEventPublisher notificationPublisher;
    private final ObjectMapper objectMapper;

    public AppointmentScheduleServiceImpl(
            AppointmentScheduleMapper scheduleMapper,
            AppointmentSlotMapper slotMapper,
            OrderAppointmentSlotMapper orderSlotMapper,
            OrderMapper orderMapper,
            ProductMapper productMapper,
            PetMapper petMapper,
            ShopMapper shopMapper,
            ShopService shopService,
            NotificationEventPublisher notificationPublisher,
            ObjectMapper objectMapper) {
        this.scheduleMapper = scheduleMapper;
        this.slotMapper = slotMapper;
        this.orderSlotMapper = orderSlotMapper;
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
        this.petMapper = petMapper;
        this.shopMapper = shopMapper;
        this.shopService = shopService;
        this.notificationPublisher = notificationPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AppointmentSlot> listPublicSlots(Integer serviceId, LocalDate from, LocalDate to) {
        // 下架商品和非 service 类型的商品一律 404，避免已停售服务的时段继续对外可约。
        Product service = getAvailableService(serviceId);
        LocalDate today = LocalDate.now();
        LocalDate windowEnd = today.plusDays(SaveAppointmentScheduleRequest.BOOKABLE_DAYS);
        // 入参强制收敛到 7 天窗口：前端传更大的区间也只能看到窗口内的时段，可约上限只在服务端说了算。
        LocalDate queryFrom = from == null || from.isBefore(today) ? today : from;
        LocalDate queryTo = to == null || to.isAfter(windowEnd) ? windowEnd : to;
        if (queryTo.isBefore(queryFrom)) {
            throw new BusinessException("查询日期范围无效");
        }
        // 门店营业状态、店铺归档、店主状态以及 end_time 是否已过都在 SQL 侧过滤，这里不重复判断。
        return slotMapper.selectPublicByServiceId(service.getId(), queryFrom, queryTo);
    }

    @Override
    public AppointmentSchedule getMerchantSchedule(
            Integer ownerId, Integer serviceId, Integer shopId) {
        resolveOwnedServiceShop(ownerId, serviceId, shopId);
        return scheduleMapper.selectByServiceId(serviceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppointmentSchedule saveSchedule(
            Integer ownerId, Integer serviceId, Integer shopId,
            SaveAppointmentScheduleRequest request) {
        Shop shop = resolveOwnedServiceShop(ownerId, serviceId, shopId);
        Product service = productMapper.selectById(serviceId);
        // 时段切分粒度只来自服务的结构化时长：未设置时长的服务无法生成时段，必须先去编辑服务补齐。
        if (service == null || service.getDurationMinutes() == null) {
            throw new BusinessException("请先在编辑服务中设置服务时长，再设置排班时间段");
        }
        // 模板先在内存里校验：时间倒置、午休跨界、容量越界的配置一旦入库，会被窗口重建批量写成坏时段。
        AppointmentSchedulePolicy.validateTemplate(request);
        // upsert 模板：一服务一份每日时间段，更新走 service_id + shop_id 双条件防止归属被改挂。
        AppointmentSchedule template = new AppointmentSchedule();
        template.setShopId(shop.getId());
        template.setServiceId(serviceId);
        template.setStartTime(request.getStartTime());
        template.setEndTime(request.getEndTime());
        template.setBreakStart(request.getBreakStart());
        template.setBreakEnd(request.getBreakEnd());
        template.setIntervalMinutes(request.getIntervalMinutes() == null ? 0 : request.getIntervalMinutes());
        template.setCapacityPerSlot(request.getCapacityPerSlot());
        template.setEnabled(true);
        if (scheduleMapper.selectByServiceId(serviceId) == null) {
            scheduleMapper.insert(template);
        } else {
            scheduleMapper.updateByServiceIdAndShopId(template, shop.getId());
        }
        // 窗口内每天默认按模板排班且全部开启：逐天重建只删未约时段，已约时段原样保留，休业天状态不丢。
        LocalDate today = LocalDate.now();
        for (LocalDate cursor = today.plusDays(1);
             !cursor.isAfter(today.plusDays(SaveAppointmentScheduleRequest.BOOKABLE_DAYS));
             cursor = cursor.plusDays(1)) {
            final LocalDate date = cursor;
            rebuildDaySlots(shop.getId(), serviceId, service, date, template);
        }
        return scheduleMapper.selectByServiceId(serviceId);
    }

    @Override
    public List<AppointmentSlot> listMerchantSlots(
            Integer ownerId, Integer serviceId, Integer shopId,
            LocalDate from, LocalDate to) {
        Shop shop = resolveOwnedServiceShop(ownerId, serviceId, shopId);
        LocalDate today = LocalDate.now();
        LocalDate windowEnd = today.plusDays(SaveAppointmentScheduleRequest.BOOKABLE_DAYS);
        // 时段只由保存模板与兜底任务生成，这里只读：窗口内每个时段自带已约人数与休业状态，供预约情况视图使用。
        LocalDate queryFrom = from == null || from.isBefore(today) ? today : from;
        LocalDate queryTo = to == null || to.isAfter(windowEnd) ? windowEnd : to;
        if (queryTo.isBefore(queryFrom)) {
            return new ArrayList<>();
        }
        return slotMapper.selectByServiceIdAndShopId(
                serviceId, shop.getId(), queryFrom, queryTo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppointmentSlot updateSlotCapacity(
            Integer ownerId, Integer slotId, Integer shopId, Integer capacity) {
        AppointmentSlot slot = getOwnedSlot(ownerId, slotId, shopId);
        // 已结束的时段冻结容量：历史预约的口径必须可追溯，改容量会把过去的余量也一起改写。
        if (isPastSlot(slot)) {
            throw new BusinessException("已过去或正在结束的时段不能修改容量");
        }
        // 容量不得低于 booked_count：一旦低于已售数，tryReserve 的 booked_count < capacity 条件将永久不成立，该时段再也约不上。
        if (capacity < slot.getBookedCount()) {
            throw new BusinessException("时段容量不能小于当前已预约数量");
        }
        // 并发保护在 SQL：updateCapacity 带 shop_id、status <> 'EXPIRED'、booked_count <= capacity 三重条件，
        // 影响行数为 0 说明期间已被约满或时段已过期，不能把过期数据当作修改成功返回。
        if (slotMapper.updateCapacity(slotId, slot.getShopId(), capacity) == 0) {
            throw new BusinessException("时段已关闭或容量修改失败");
        }
        return slotMapper.selectById(slotId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeSlot(Integer ownerId, Integer slotId, Integer shopId) {
        AppointmentSlot slot = getOwnedSlot(ownerId, slotId, shopId);
        // 已过去的时段不再允许改状态：可售性只对未来的时段有意义。
        if (isPastSlot(slot)) {
            throw new BusinessException("已经过去的时段不能关闭");
        }
        // 已有 booked_count 的时段不能关闭：关掉后这些已确认的预约就没有可履约的时段承载。
        if (slot.getBookedCount() > 0) {
            throw new BusinessException("该时段已有预约，请先处理预约后再关闭");
        }
        // close 的条件是 status = 'OPEN' 且 booked_count = 0，影响行数为 0 表示并发下刚被预约或状态已被改写。
        if (slotMapper.close(slotId, slot.getShopId()) == 0) {
            throw new BusinessException("时段状态已变化，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void openSlot(Integer ownerId, Integer slotId, Integer shopId) {
        AppointmentSlot slot = getOwnedSlot(ownerId, slotId, shopId);
        // 过去的时段开启后依然不可约，直接拒绝，避免留下无效状态。
        if (isPastSlot(slot)) {
            throw new BusinessException("已经过去的时段不能开启");
        }
        // open 只对 CLOSED 生效，重复开启影响行数为 0，按「状态已变化」提示刷新。
        if (slotMapper.open(slotId, slot.getShopId()) == 0) {
            throw new BusinessException("时段状态已变化，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int setDayStatus(Integer ownerId, Integer serviceId, Integer shopId,
                            LocalDate slotDate, String dayStatus, String reason) {
        Shop shop = resolveOwnedServiceShop(ownerId, serviceId, shopId);
        // day_status 只有 OPEN/CLOSED 两种取值，统一大写后比对，非法值不落库，否则公开查询的 day_status = 'OPEN' 过滤会失效。
        String normalized = dayStatus == null ? "" : dayStatus.trim().toUpperCase();
        if (!DAY_OPEN.equals(normalized) && !DAY_CLOSED.equals(normalized)) {
            throw new BusinessException("不支持的天级状态");
        }
        // 只能调整今天之后的日期：当天时段可能已经开始，改状态会让正在服务的预约口径混乱。
        if (slotDate == null || !slotDate.isAfter(LocalDate.now())) {
            throw new BusinessException("过去或当天的日期不能调整营业状态");
        }
        int cancelledCount = 0;
        if (DAY_CLOSED.equals(normalized)) {
            // 休业即取消该天全部未来已确认预约：条件更新防并发，取消成功才释放名额，避免重复回减 booked_count。
            Product service = getAvailableService(serviceId);
            List<Order> bookings = orderMapper.selectConfirmedBookingsForDay(
                    serviceId, shop.getId(), slotDate);
            for (Order booking : bookings) {
                if (orderMapper.updateStatusIfCurrent(
                        booking.getId(),
                        OrderStatus.CONFIRMED.getCode(),
                        OrderStatus.CANCELLED.getCode()) == 0) {
                    continue;
                }
                orderSlotMapper.releaseCapacityByOrderId(booking.getId(), shop.getId());
                publishBookingCancelledByMerchant(booking, service, reason);
                cancelledCount++;
            }
        }
        // 天级状态按 service + shop + 日期整批写进每个时段行的 day_status，一条 UPDATE 就让整天在公开列表里不可售；
        // 恢复营业后未约时段重新可约，已取消订单不受影响。
        slotMapper.updateDayStatus(serviceId, shop.getId(), slotDate, normalized);
        return cancelledCount;
    }

    /** 休业取消通知：原因必须传达到用户，dedupeKey 幂等，事件在事务提交后落库，取消回滚不会留下假通知。 */
    private void publishBookingCancelledByMerchant(Order order, Product service, String reason) {
        List<AppointmentSlot> slots = slotMapper.selectByOrderIds(List.of(order.getId()));
        String safeReason = reason == null || reason.isBlank() ? "商家排班调整" : reason.trim();
        notificationPublisher.publish(new NotificationEvent(
                order.getUserId(), null, ShopType.SERVICE.getCode(),
                "cancelled", "warning",
                "您的预约已被商家取消",
                "预约号：" + order.getOrderNo()
                        + "\n服务：" + service.getName()
                        + "\n预约时段：" + formatSlots(slots)
                        + "\n金额：¥" + order.getTotalAmount()
                        + "\n取消原因：" + safeReason,
                "order", order.getId().longValue(), "/me",
                "booking-merchant-cancelled:" + order.getId()));
    }

    // 兜底任务入口：调用方 DailyScheduleJob 只有 shop/service 维度、没有 ownerId，因此这里不做归属校验。
    public int ensureSlotsForJob(Integer shopId, Integer serviceId) {
        Product service = productMapper.selectById(serviceId);
        if (service == null || service.getDurationMinutes() == null) {
            return 0;
        }
        AppointmentSchedule template = scheduleMapper.selectByServiceId(serviceId);
        if (template == null || !Boolean.TRUE.equals(template.getEnabled())) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        int generated = 0;
        // 只补「无时段」的天：保存模板是时段的唯一权威来源，任务不做部分重建，避免刷掉商家保存后的状态。
        for (LocalDate cursor = today.plusDays(1);
             !cursor.isAfter(today.plusDays(SaveAppointmentScheduleRequest.BOOKABLE_DAYS));
             cursor = cursor.plusDays(1)) {
            final LocalDate date = cursor;
            if (slotMapper.countByServiceIdAndShopIdAndDate(serviceId, shopId, date) > 0) {
                continue;
            }
            generated += rebuildDaySlots(shopId, serviceId, service, date, template);
        }
        return generated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingCreationResponse createBooking(
            Integer userId, Integer serviceId, Integer petId,
            List<Integer> slotIds, String note) {
        // 服务必须上架且 product_type = service，否则其时段不允许再被下单。
        Product service = getAvailableService(serviceId);
        // 门店由服务记录决定，并用 selectPublicById 校验营业与归档状态，不信任前端传门店。
        Shop serviceShop = shopMapper.selectPublicById(service.getShopId());
        if (serviceShop == null) {
            throw new BusinessException(404, "服务门店暂未营业");
        }
        // 宠物必须属于当前用户；查不到统一返回 404，不区分「不存在」与「不是你的」，避免探测他人 petId。
        Pet pet = petMapper.selectByIdAndUserId(petId, userId);
        if (pet == null) {
            throw new BusinessException(404, "宠物档案不存在");
        }

        // 去重并限制一次最多 8 段：validateAndSort 再把时段统一按 slot_date/start_time 排序，让后面逐个 tryReserve 的更新顺序稳定，减少并发互等。
        List<Integer> normalizedSlotIds = AppointmentSlotPolicy.normalizeSlotIds(slotIds);
        List<AppointmentSlot> slots =
                new ArrayList<>(slotMapper.selectByIds(normalizedSlotIds));
        // 数量不一致说明有 id 不存在；validate 再逐段校验归属服务、status = OPEN、未约满、开始时间尚未到。
        AppointmentSlotPolicy.validateAndSort(service, normalizedSlotIds, slots);
        // 全部时段的容量占用与订单写入在同一事务：任一段占用失败都会抛异常，整单回滚，前面已占的 booked_count 一并撤销。
        reserveSlots(slots);

        // 金额只由服务单价 × 占用时段数决定，不接受前端金额，避免改价下单。
        BigDecimal totalAmount = service.getPrice()
                .multiply(BigDecimal.valueOf(slots.size()));
        Order order = new Order();
        order.setOrderNo(OrderNumberGenerator.next());
        order.setUserId(userId);
        order.setShopId(service.getShopId());
        order.setOrderType(OrderType.BOOKING.getCode());
        order.setTotalAmount(totalAmount);
        // 预约单提交即 confirmed，跳过 pending_payment：容量在这一刻已被占用，不能再走待支付超时取消，否则名额会悬空。
        order.setStatus(OrderStatus.CONFIRMED.getCode());
        order.setAppointmentTime(toStartDateTime(slots.get(0)));
        // 结束时间取最后一段的 end_time：跨时段预约不能靠「开始时间 + 时长」推算，否则会低估占用窗口。
        AppointmentSlot lastSlot = slots.get(slots.size() - 1);
        order.setAppointmentEndTime(LocalDateTime.of(
                lastSlot.getSlotDate(), lastSlot.getEndTime()));
        order.setAppointmentDuration(slots.size() + "个时段");
        order.setAppointmentPetName(pet.getName());
        // items 落地服务名与单价快照，服务后续改价或改名不会追溯影响已生成的订单。
        order.setItems(writeJson(service, slots.size()));
        // 预约单没有收货地址，复用 shipping_address 存备注 JSON，读取方需按 note 解析，不能当地址展示。
        order.setShippingAddress(writeJson(note == null ? "" : note.trim()));
        orderMapper.insert(order);

        // order_appointment_slots 是 releaseCapacityByOrderId 释放容量的唯一依据，必须与订单、容量占用同事务写入。
        List<OrderAppointmentSlot> links = new ArrayList<>(slots.size());
        for (AppointmentSlot slot : slots) {
            OrderAppointmentSlot link = new OrderAppointmentSlot();
            link.setOrderId(order.getId());
            link.setSlotId(slot.getId());
            link.setPetId(petId);
            links.add(link);
        }
        orderSlotMapper.batchInsert(links);
        // 通知以事件形式在事务提交后才落库，订单回滚时不会留下「预约成功」的假通知。
        publishBookingCreated(order, service, pet, slots, note);
        return new BookingCreationResponse(
                order.getId(), order.getOrderNo(), slots.size(), totalAmount);
    }

    /**
     * 重建某天时段：先删该天 booked_count = 0 的未约时段（已约时段行保留，旧订单引用不断），
     * 再按模板以「服务时长 + 服务间隔」为步长切分工作区间生成新时段；
     * 该天处于休业时（由保留的已约行 day_status 判断），重建后补写休业，避免新时段把休业天洗成可约。
     */
    private int rebuildDaySlots(
            Integer shopId, Integer serviceId, Product service,
            LocalDate date, AppointmentSchedule template) {
        List<AppointmentSlot> existing = slotMapper.selectByServiceIdAndShopId(
                serviceId, shopId, date, date);
        // 休业状态挂在时段行上：delete 只动未约行，已约行的 day_status 就是该天休业与否的唯一事实来源。
        boolean closedDay = existing.stream()
                .anyMatch(slot -> DAY_CLOSED.equals(slot.getDayStatus()));
        // 两步清理：无引用的未约时段物理删除；被取消/完成订单引用的未约时段隐藏（FK 保留历史凭证），
        // 后者生成时段命中同一 start_time 时由 refreshUnbookedTemplate 恢复可约。
        slotMapper.deleteByServiceIdAndShopIdAndDate(serviceId, shopId, date);
        slotMapper.hideReferencedUnbookedSlots(serviceId, shopId, date);
        if (template == null) {
            return 0;
        }
        int generated = generateTemplateSlots(shopId, serviceId, service, date, template);
        if (closedDay) {
            // 整批补写休业：新时段与保留的已约行状态重新对齐，公开列表继续隐藏该天。
            slotMapper.updateDayStatus(serviceId, shopId, date, DAY_CLOSED);
        }
        return generated;
    }

    /** 按模板生成某天时段：午休把营业时间切成 1~2 个工作区间，区间内按步长推进，间隔只影响下一个起点。 */
    private int generateTemplateSlots(
            Integer shopId, Integer serviceId, Product service,
            LocalDate date, AppointmentSchedule template) {
        int durationMinutes = service.getDurationMinutes();
        // 切分步长 = 服务时长 + 排班模板里的服务间隔：间隔归排班管理，服务编辑不再提供。
        int stepMinutes = durationMinutes
                + (template.getIntervalMinutes() == null ? 0 : template.getIntervalMinutes());
        int generated = 0;
        for (LocalTime[] range : splitWorkRanges(template)) {
            LocalTime start = range[0];
            // 每段服务必须完整落在工作区间内：结束越界的碎余量直接丢弃，保证时段时长一致。
            while (!start.plusMinutes(durationMinutes).isAfter(range[1])) {
                LocalTime end = start.plusMinutes(durationMinutes);
                generated += insertOrRefreshSlot(
                        shopId, serviceId, date, start, end, template.getCapacityPerSlot());
                start = start.plusMinutes(stepMinutes);
            }
        }
        return generated;
    }

    /** 按午休区间把营业时间切分工作区间：无午休返回整段，午休两侧切不出完整一段服务的碎区间丢弃。 */
    private List<LocalTime[]> splitWorkRanges(AppointmentSchedule template) {
        if (template.getBreakStart() == null || template.getBreakEnd() == null) {
            return List.<LocalTime[]>of(new LocalTime[]{template.getStartTime(), template.getEndTime()});
        }
        List<LocalTime[]> ranges = new ArrayList<>(2);
        // 午休前区间：开始早于午休开始才有效，相等说明午休从营业一开始就占用，上午无时段。
        if (template.getStartTime().isBefore(template.getBreakStart())) {
            ranges.add(new LocalTime[]{template.getStartTime(), template.getBreakStart()});
        }
        // 午休后区间：午休结束早于营业结束才有效，午休延伸到打烊则下午无时段。
        if (template.getBreakEnd().isBefore(template.getEndTime())) {
            ranges.add(new LocalTime[]{template.getBreakEnd(), template.getEndTime()});
        }
        return ranges;
    }

    /** 同一 service + date + start_time 已存在时只刷新未预约时段的 end_time/capacity，不重建行，避免把已售时段的容量冲掉；不存在的走 INSERT IGNORE，由唯一键让并发重复插入退化为 0 行。 */
    private int insertOrRefreshSlot(
            Integer shopId, Integer serviceId, LocalDate date,
            LocalTime startTime, LocalTime endTime, Integer capacityPerSlot) {
        AppointmentSlot slot = new AppointmentSlot();
        slot.setShopId(shopId);
        slot.setServiceId(serviceId);
        slot.setSlotDate(date);
        slot.setStartTime(startTime);
        slot.setEndTime(endTime);
        slot.setCapacity(capacityPerSlot);
        // 新时段一律 OPEN，休业天的状态由 rebuildDaySlots 在重建后统一补写，这里不做天级判断。
        slot.setDayStatus(DAY_OPEN);
        slot.setStatus(AppointmentSlotStatus.OPEN.getCode());
        int existingCount = slotMapper.countByServiceIdAndDateAndStartTime(serviceId, date, startTime);
        if (existingCount > 0) {
            // 重建后仍存在的行必然是已约时段或被订单引用的历史时段（未引用的已删除），
            // refresh 只对未约行生效：已约行容量保持原值，被隐藏的引用行按新规则恢复可约。
            slotMapper.refreshUnbookedTemplate(slot);
            return 0;
        }
        return slotMapper.insert(slot) == 1 ? 1 : 0;
    }

    private Product getAvailableService(Integer serviceId) {
        Product service = productMapper.selectById(serviceId);
        // 下架或类型不是 service 的商品按 404 处理，不向调用方区分「不存在」与「不可售」，避免探测商品状态。
        if (service == null
                || !ProductStatus.ON.getCode().equals(service.getStatus())
                || !ProductType.SERVICE.getCode().equals(service.getProductType())) {
            throw new BusinessException(404, "服务不存在或已下架");
        }
        return service;
    }

    private Shop resolveOwnedServiceShop(Integer ownerId, Integer serviceId, Integer shopId) {
        Product service = getAvailableService(serviceId);
        // 归属以服务记录上的 shop_id 为准：getOpenShop 会校验该门店属于 ownerId 且处于营业状态。
        Shop shop = shopService.getOpenShop(ownerId, ShopType.SERVICE.getCode(), service.getShopId());
        // 入参 shopId 只做一致性比对，不一致说明请求试图用别的门店身份操作本店服务，按越权 403 拒绝。
        if (shopId != null && !shopId.equals(shop.getId())) {
            throw new BusinessException(403, "服务不属于当前店铺");
        }
        return shop;
    }

    private AppointmentSlot getOwnedSlot(Integer ownerId, Integer slotId, Integer shopId) {
        AppointmentSlot slot = slotMapper.selectById(slotId);
        if (slot == null) {
            throw new BusinessException(404, "预约时段不存在");
        }
        Shop shop = resolveOwnedServiceShop(ownerId, slot.getServiceId(), shopId);
        if (!shop.getId().equals(slot.getShopId())) {
            throw new BusinessException(403, "无权操作该预约时段");
        }
        return slot;
    }

    private void reserveSlots(List<AppointmentSlot> slots) {
        for (AppointmentSlot slot : slots) {
            // 容量占用只走条件更新：tryReserve 的 WHERE 带 status = 'OPEN' AND booked_count < capacity，
            // 影响行数为 0 表示期间已被其他请求约满或时段被关闭，抛 409 让整个下单事务回滚，是防超卖的唯一闸口。
            if (slotMapper.tryReserve(slot.getId()) == 0) {
                throw new BusinessException(409, "时段[" + formatSlot(slot)
                        + "]刚刚被约满，请刷新后重新选择");
            }
        }
    }

    private void publishBookingCreated(
            Order order, Product service, Pet pet,
            List<AppointmentSlot> slots, String note) {
        // dedupeKey 固定为 booking-user-created:订单ID，重复提交或事件重放都不会写出第二条站内信。
        notificationPublisher.publish(new NotificationEvent(
                order.getUserId(), null, ShopType.SERVICE.getCode(),
                "confirmed", "info", "预约提交成功",
                "预约号：" + order.getOrderNo()
                        + "\n服务：" + service.getName()
                        + "\n宠物：" + pet.getName()
                        + "\n预约时段：" + formatSlots(slots),
                "order", order.getId().longValue(), "/me",
                "booking-user-created:" + order.getId()));
        try {
            Shop shop = shopMapper.selectById(service.getShopId());
            // 门店没有 owner 时直接返回：无人接收的通知写进去只会变成脏数据。
            if (shop == null || shop.getOwnerId() == null) {
                return;
            }
            StringBuilder content = new StringBuilder()
                    .append("预约号：").append(order.getOrderNo())
                    .append("\n服务：").append(service.getName())
                    .append("\n宠物：").append(pet.getName())
                    .append("\n金额：¥").append(order.getTotalAmount())
                    .append("\n预约时段：").append(formatSlots(slots))
                    .append("\n备注：").append(note == null || note.isBlank() ? "无" : note);
            notificationPublisher.publish(new NotificationEvent(
                    shop.getOwnerId(), order.getUserId(), ShopType.SERVICE.getCode(),
                    "new_booking", "info",
                    "您有新的预约订单", content.toString(), "order",
                    order.getId().longValue(), "/merchant/services",
                    "booking-created:" + order.getId()));
        } catch (RuntimeException exception) {
            // 商家通知是附加动作：失败只记 warn，预约订单已经确认，不能因为通知异常把下单结果一起回滚。
            log.warn("预约创建成功，但商家通知发送失败，订单号：{}",
                    order.getOrderNo(), exception);
        }
    }

    private String writeJson(Product service, int quantity) {
        List<OrderItemDTO> items = List.of(
                new OrderItemDTO(
                        service.getId(), service.getName(), service.getPrice(), quantity));
        return writeJson(items);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            // 快照序列化失败按 500 抛出，让下单事务回滚，避免落一份 items 或备注缺失的订单。
            throw new BusinessException(500, "预约数据序列化失败");
        }
    }

    private LocalDateTime toStartDateTime(AppointmentSlot slot) {
        return LocalDateTime.of(slot.getSlotDate(), slot.getStartTime());
    }

    private boolean isPastSlot(AppointmentSlot slot) {
        return AppointmentSlotPolicy.isPast(slot);
    }

    private String formatSlots(List<AppointmentSlot> slots) {
        return slots.stream()
                .map(this::formatSlot)
                .reduce((left, right) -> left + "；" + right)
                .orElse("无");
    }

    private String formatSlot(AppointmentSlot slot) {
        return slot.getSlotDate().format(DateTimeFormatter.ofPattern("MM月dd日"))
                + " " + slot.getStartTime().format(SLOT_TIME_FORMATTER)
                + "-" + slot.getEndTime().format(SLOT_TIME_FORMATTER);
    }
}
