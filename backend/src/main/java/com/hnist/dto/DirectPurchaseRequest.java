package com.hnist.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 立即购买入参（POST /api/orders/direct）：绕过购物车，直接按单商品下单并生成订单。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DirectPurchaseRequest {

    @NotNull(message = "商品ID不能为空")
    private Integer productId;

    @NotNull(message = "商品数量不能为空")
    @Min(value = 1, message = "商品数量不能小于1")
    private Integer quantity;

    @NotBlank(message = "收货地址不能为空")
    private String shippingAddress;
}
