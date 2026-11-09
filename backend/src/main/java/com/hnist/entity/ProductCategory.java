package com.hnist.entity;

import lombok.Data;

/** product_categories 表行映射：商品与服务分类树，parentId 指向上一级分类。 */
@Data
public class ProductCategory {

    private Long id;

    private Long parentId;

    /** goods、service */
    private String productType;

    private String categoryCode;

    private String categoryName;

    /** 层级，1 为一级分类 */
    private Integer categoryLevel;

    private Integer sortOrder;

    private Boolean enabled;

    private Boolean medicalFlag;

    /** 非DB字段，分类下商品/服务的数量统计 */
    private Integer itemCount;
}
