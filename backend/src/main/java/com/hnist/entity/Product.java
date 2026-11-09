package com.hnist.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** products 表行映射：商品与服务共用一张表，用 productType 区分 goods、service。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer shopId;
    /** goods, service */
    private String productType;
    private String category;
    private Long categoryId;
    private String categoryCode;
    private String categoryName;
    private String parentCategoryCode;
    private String parentCategoryName;
    private Boolean medicalCategory;
    private String name;
    private String description;
    /** 适用宠物：CAT、DOG、ALL */
    private String targetPet;
    /** 适用阶段：YOUNG、ADULT、SENIOR、ALL */
    private String lifeStage;
    private String healthTags;
    /** 单价，decimal(10,2) */
    private BigDecimal price;
    /** 剩余库存件数 */
    private Integer stock;
    /** 服务时长展示文本（由 durationMinutes 生成，仅作展示） */
    private String duration;
    /** 服务时长分钟数：排班时段切分的唯一依据，service 类型必填；服务间隔由排班模板管理 */
    private Integer durationMinutes;
    private String imageUrl;
    /** 上下架状态：on、off */
    private String status;
    private LocalDateTime createdAt;
    /** 非DB字段，店铺名称 */
    private String shopName;
    /** 非DB字段，店铺评分 */
    private BigDecimal shopRating;
    /** 非DB字段，店铺营业时间 */
    private String shopBusinessHours;
    /** 非DB字段，店铺地址 */
    private String shopAddress;
    /** 非DB字段，店铺主用户ID */
    private Integer shopOwnerId;
    /** 非DB字段，店铺Logo */
    private String shopLogo;
}
