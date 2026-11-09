package com.hnist.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 加入购物车入参（POST /api/cart）：只收上架的 goods，同一商品重复加购按数量累加。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddToCartRequest {

    @NotNull(message = "商品ID不能为空")
    private Integer productId;

    @NotNull(message = "商品数量不能为空")
    @Min(value = 1, message = "商品数量不能小于1")
    private Integer quantity;
}
