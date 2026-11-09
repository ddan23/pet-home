package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 下单结果：购物车结算按店铺拆单返回多笔 id，直接购买只回一笔。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreationResponse {

    /** 批量结算产生的订单 id，与 orderId 二选一；跨店商品会拆成多笔。 */
    private List<Integer> orderIds;

    /** 直接购买的订单 id，批量结算时为 null。 */
    private Integer orderId;

    /** 结算时因已下架或暂停销售被跳过的商品名，前端据此提示失效项。 */
    private List<String> skippedItemNames;

    /** 跳过条数，等于 skippedItemNames 的大小。 */
    private Integer skippedCount;

    public static OrderCreationResponse batch(List<Integer> orderIds) {
        return batch(orderIds, List.of());
    }

    public static OrderCreationResponse batch(List<Integer> orderIds,
                                              List<String> skippedItemNames) {
        return new OrderCreationResponse(orderIds, null, skippedItemNames,
                skippedItemNames.size());
    }

    public static OrderCreationResponse single(Integer orderId) {
        return new OrderCreationResponse(null, orderId, List.of(), 0);
    }
}
