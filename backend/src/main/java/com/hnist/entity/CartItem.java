package com.hnist.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** cart_items 表行映射：购物车行，user_id 与 product_id 组合唯一。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer userId;
    private Integer productId;
    private Integer quantity;
    /** 是否勾选参与结算 */
    private Boolean checked;
    private LocalDateTime createdAt;
    /** 非DB字段 */
    private String productName;
    /** 非DB字段 */
    private java.math.BigDecimal productPrice;
    /** 非DB字段 */
    private String productImage;
    /** 非DB字段 */
    private String shopName;
    /** 非DB字段 */
    private Integer shopId;
    /** 非DB字段 */
    private String shopLogo;
    /** 非DB字段 */
    private String productStatus;
    /** 非DB字段，当前是否允许结算 */
    private Boolean available;
    /** 非DB字段，不可结算原因 */
    private String unavailableReason;
}
