package com.hnist.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 购物车结算入参（POST /api/orders）：商品来自已勾选的购物车行并按店铺拆单，请求体只需收货地址。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    // 地址以 JSON 快照整段写入订单，落库前校验必填项与手机号格式，避免脏地址固化进历史订单。
    @NotBlank(message = "收货地址不能为空")
    private String shippingAddress;
}
