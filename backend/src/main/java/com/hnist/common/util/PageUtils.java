package com.hnist.common.util;

/** 分页参数边界工具：页码下限 1，页大小夹在 [1, 上限] 之间，避免负数 offset 与超大页拖垮查询。 */
public final class PageUtils {

    /** 未指定业务上限时的默认 pageSize 上限，避免调用方漏传导致一次拉全表。 */
    private static final int DEFAULT_MAX_PAGE_SIZE = 100;

    private PageUtils() {
    }

    /** 页码小于 1（含 0 与负数）一律按第 1 页处理，不抛异常。 */
    public static int normalizePage(int page) {
        return Math.max(page, 1);
    }

    /** 按默认上限 100 收口，适合没有特殊分页要求的列表接口。 */
    public static int normalizePageSize(int pageSize) {
        return normalizePageSize(pageSize, DEFAULT_MAX_PAGE_SIZE);
    }

    /** 上限由调用方按业务给定（如社区评论 50）；非正数页大小归 1，maximum 必须为正数。 */
    public static int normalizePageSize(int pageSize, int maximum) {
        return Math.min(Math.max(pageSize, 1), maximum);
    }
}
