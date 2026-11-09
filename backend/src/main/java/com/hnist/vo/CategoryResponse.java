package com.hnist.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 商品/服务分类树节点：只包含 enabled=1 的分类，父级被停用的子节点整体丢弃。 */
@Data
public class CategoryResponse {

    private Long id;

    /** 分类业务编码，与 categoryName 分属两种标识，前端筛选用它。 */
    private String code;

    private String name;

    /** 层级，1 为一级、2 为二级；商品只能挂二级分类。 */
    private Integer level;

    private Integer sortOrder;

    /** 医疗类标记，供前端展示科室类入口。 */
    private Boolean medical;

    /**
     * 在售条目数：只统计上架商品，且要求店铺 open、商家账号 normal。
     * 一级分类自身不含商品，返回的是子分类计数之和，因此不会因 level=1 恒为 0 被当成空分类过滤掉。
     */
    private Integer itemCount;

    private List<CategoryResponse> children = new ArrayList<>();
}
