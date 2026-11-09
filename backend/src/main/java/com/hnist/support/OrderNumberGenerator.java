package com.hnist.support;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/** 订单号生成器：毫秒时间戳 + 6 位随机后缀，格式 yyyyMMddHHmmssSSS + 后缀，共 20 位。 */
public final class OrderNumberGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private OrderNumberGenerator() {
    }

    /** 随机后缀降低同一毫秒内并发碰撞概率；ThreadLocalRandom 避免多线程争用同一随机源。 */
    public static String next() {
        int randomSuffix = ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
        return FORMATTER.format(LocalDateTime.now()) + randomSuffix;
    }
}
