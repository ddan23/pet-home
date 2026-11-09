package com.hnist.common.constant;

import lombok.Getter;

/** 可预约时段状态，落 appointment_slots.status；对外展示的“约满”由容量现场推导，不落库。 */
@Getter
public enum AppointmentSlotStatus {

    /** 开放：排班生成时段时写入，只有该状态能被下单占用，占用用条件更新保证不超卖。 */
    OPEN("OPEN"),
    /** 已关闭：商家手工关闭且该时段尚无预约时写入，之后可重新 open。 */
    CLOSED("CLOSED"),
    /** 已过期：失效时段，修改容量时被排除，避免改写已过期的历史数据。 */
    EXPIRED("EXPIRED"),
    /** 已约满：booked_count >= capacity 时由查询 SQL 派生的展示值，库中不存该状态。 */
    FULL("FULL");

    private final String code;

    AppointmentSlotStatus(String code) {
        this.code = code;
    }
}
