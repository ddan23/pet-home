package com.hnist.policy;

import com.hnist.common.constant.AppointmentSlotStatus;
import com.hnist.common.exception.BusinessException;
import com.hnist.entity.AppointmentSlot;
import com.hnist.entity.Product;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** 预约时段选择规则：下单前对时段做归属、可售性与时间合法性收敛，并保证后续占用顺序稳定。 */
public final class AppointmentSlotPolicy {

    // 单次预约上限：防止一次请求锁住过多时段的 booked_count，拉长事务内条件更新的互等时间。
    private static final int MAX_SLOT_COUNT = 8;

    private AppointmentSlotPolicy() {
    }

    /** 入参先去重、限制 8 段以内、再按 id 排序：调用方 createBooking 依赖稳定顺序逐段 tryReserve，重复 id 会被重复计数收费。 */
    public static List<Integer> normalizeSlotIds(List<Integer> slotIds) {
        if (slotIds == null || slotIds.isEmpty()) {
            throw new BusinessException("请至少选择一个预约时段");
        }
        List<Integer> result = new ArrayList<>(new HashSet<>(slotIds));
        if (result.size() > MAX_SLOT_COUNT) {
            throw new BusinessException("一次最多选择8个预约时段");
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    /**
     * 下单前的最后一道校验：数量不一致说明有 id 不存在（可能刚被商家删除），
     * 逐段校验归属服务/门店、status = OPEN、未约满、开始时间尚未到；
     * 任一条件失守都会让用户占用不存在的容量或已售时段，随后 tryReserve 的条件更新与这里共同兜底。
     * 校验后按 slot_date/start_time 排序，让占用顺序与时段展示顺序一致，减少并发事务互相等待。
     */
    public static void validateAndSort(
            Product service, List<Integer> requestedIds, List<AppointmentSlot> slots) {
        if (slots.size() != requestedIds.size()) {
            throw new BusinessException("部分预约时段不存在，请刷新后重试");
        }
        LocalDateTime now = LocalDateTime.now();
        for (AppointmentSlot slot : slots) {
            validate(service, slot, now);
        }
        slots.sort(Comparator
                .comparing(AppointmentSlot::getSlotDate)
                .thenComparing(AppointmentSlot::getStartTime));
    }

    /** 结束时间一过即视为过去：调用方用它冻结容量修改与关闭操作，历史时段的口径必须可追溯。 */
    public static boolean isPast(AppointmentSlot slot) {
        return !LocalDateTime.of(slot.getSlotDate(), slot.getEndTime())
                .isAfter(LocalDateTime.now());
    }

    /** 四道校验缺一不可：跨服务/跨门店时段会污染别家库存，非 OPEN 或已满时段不可再售，开始时间已过的时段无法履约。 */
    private static void validate(Product service, AppointmentSlot slot, LocalDateTime now) {
        if (!service.getId().equals(slot.getServiceId())
                || !service.getShopId().equals(slot.getShopId())) {
            throw new BusinessException("预约时段不属于当前服务");
        }
        if (!AppointmentSlotStatus.OPEN.getCode().equals(slot.getStatus())) {
            throw new BusinessException("所选时段已关闭或已约满");
        }
        if (slot.getBookedCount() >= slot.getCapacity()) {
            throw new BusinessException("所选时段已约满，请重新选择");
        }
        if (!LocalDateTime.of(slot.getSlotDate(), slot.getStartTime()).isAfter(now)) {
            throw new BusinessException("不能预约已经开始或已经过去的时段");
        }
    }
}
