package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/** 订单明细行：以 JSON 快照写入 orders.items，商品后续改名改价不影响历史订单展示。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class

OrderItemDTO {
    private Integer productId;
    /** 下单时的商品名快照。 */
    private String name;
    /** 下单时的成交单价（元）。 */
    private BigDecimal price;
    private Integer quantity;
}
