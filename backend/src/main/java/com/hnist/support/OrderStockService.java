package com.hnist.support;

import com.hnist.common.constant.OrderType;
import com.hnist.common.exception.BusinessException;
import com.hnist.vo.OrderItemDTO;
import com.hnist.mapper.ProductMapper;
import com.hnist.entity.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/** 订单商品库存协作：下单原子扣减、取消/超时后按订单明细回补。 */
@Slf4j
@Service
public class OrderStockService {

    private final ProductMapper productMapper;
    private final ObjectMapper objectMapper;

    public OrderStockService(ProductMapper productMapper, ObjectMapper objectMapper) {
        this.productMapper = productMapper;
        this.objectMapper = objectMapper;
    }

    /** 单条条件更新原子扣减库存，受影响行数为 0 即库存不足（或商品不存在），返回 false 而非先查后扣。 */
    public boolean tryReserve(Integer productId, int quantity) {
        return productMapper.reserveStock(productId, quantity) > 0;
    }

    /**
     * 按订单 items JSON 逐项回补库存；仅商品类订单（OrderType.PRODUCT）需要回补，其他类型直接跳过。
     * 任一项释放失败（releaseStock 返回 0）抛 BusinessException 中止后续项，
     * items 反序列化失败抛 BusinessException(500)；调用方应保证在事务内执行以整体回滚。
     */
    public void restore(Order order) {
        if (!OrderType.PRODUCT.getCode().equals(order.getOrderType())) {
            return;
        }
        try {
            List<OrderItemDTO> items = objectMapper.readValue(order.getItems(),
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, OrderItemDTO.class));
            for (OrderItemDTO item : items) {
                if (item.getProductId() != null && item.getQuantity() != null
                        && item.getQuantity() > 0
                        && productMapper.releaseStock(
                        item.getProductId(), item.getQuantity()) == 0) {
                    throw new BusinessException(
                            "商品库存释放失败，请稍后重试");
                }
            }
        } catch (JsonProcessingException exception) {
            log.error("恢复订单库存失败，订单号：{}", order.getOrderNo(), exception);
            throw new BusinessException(500, "取消订单时恢复库存失败");
        }
    }
}
