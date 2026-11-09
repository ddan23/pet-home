package com.hnist.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 购物车行数量修改入参（PUT /api/cart/{id}/quantity）：覆盖语义，写入的是目标件数而非增量。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateQuantityRequest {

    @NotNull(message = "商品数量不能为空")
    @Min(value = 1, message = "商品数量不能小于1")
    private Integer quantity;
}
