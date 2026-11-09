package com.hnist.service;

import com.hnist.entity.CartItem;

import java.util.List;

/** 购物车条目的维护与结算前的可售性校验。 */
public interface CartService {

    // 返回的 checked 由 SQL 现算：商品失效时强制为未勾选，并带 unavailableReason 说明原因。
    List<CartItem> listCartItems(Integer userId);

    // 返回 quantity 之和，即商品总件数，不是行数。
    int countCartItems(Integer userId);

    // 同一商品重复加购按累加处理；quantity 必须 ≥ 1，且只接受上架中的 goods。
    void addToCart(Integer userId, Integer productId, Integer quantity);

    // 覆盖为目标数量，并校验归属与库存。
    void updateQuantity(Integer cartId, Integer userId, Integer quantity);

    // 勾选时校验商品仍可售，取消勾选不做校验。
    void toggleChecked(Integer cartId, Integer userId);

    // 越权与不存在统一按 404 处理。
    void removeItem(Integer cartId, Integer userId);

    // 全选只对可用商品生效，失效商品在 SQL 中被强制取消勾选。
    void checkAll(Integer userId, boolean checked);

    // 仅作用于指定店铺的购物车行，跨店条目的勾选状态不变。
    void checkByShop(Integer userId, Integer shopId, boolean checked);
}
