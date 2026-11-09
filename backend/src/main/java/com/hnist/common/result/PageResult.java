package com.hnist.common.result;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 分页响应：total 是满足条件的全量条数（独立 count 语句统计，不带 LIMIT），page 从 1 开始，
 * pageSize 由 PageUtils 夹到 1~100，totalPages 按 total 向上取整。
 */
@Data
@NoArgsConstructor
public class PageResult<T> {
    // 仅当前页数据；判断是否还有下一页请看 total / totalPages，而不是 list 的长度。
    private List<T> list;
    private Integer total;
    private Integer page;
    private Integer pageSize;
    private Integer totalPages;
    // 与列表筛选解耦的附加统计（如用户列表的 normalCount / disabledCount），供页面顶部卡片直接取用。
    private Map<String, Object> extra;

    public PageResult(List<T> list, Integer total, Integer page, Integer pageSize) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        // 包装类型可能为 null：缺省按 0 条、每页 1 条计算，保证 totalPages 恒定有值而不是抛 NPE。
        int totalCount = total == null ? 0 : total;
        int size = pageSize == null || pageSize <= 0 ? 1 : pageSize;
        this.totalPages = (int) Math.ceil((double) totalCount / size);
    }
}
