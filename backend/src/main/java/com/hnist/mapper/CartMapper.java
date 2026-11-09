package com.hnist.mapper;

import com.hnist.entity.CartItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 购物车数据访问层 - 购物车表CRUD操作
 */
public interface CartMapper {

    // 根据主键查询购物车项
    CartItem selectById(@Param("id") Integer id);

    // 查询结果中 checked/available 为动态计算列：商品不可售（下架/非 goods/无库存/店铺停业/商家异常）时强制按未选中返回
    List<CartItem> selectByUserId(@Param("userId") Integer userId);

    int countQuantityByUserId(@Param("userId") Integer userId);

    // 根据用户ID和商品ID查询购物车项
    CartItem selectByUserAndProduct(@Param("userId") Integer userId, @Param("productId") Integer productId);

    // 添加商品到购物车
    int insert(CartItem cartItem);

    // 更新购物车项数量
    int updateQuantity(@Param("id") Integer id, @Param("quantity") Integer quantity);

    // 切换单个购物车项的选中状态
    int updateChecked(@Param("id") Integer id, @Param("checked") Boolean checked);

    // 勾选受商品可售条件约束：checked = 1 时不可售商品仍会被置 0，防止不可售商品被结算
    int updateCheckedByUserId(@Param("userId") Integer userId, @Param("checked") Boolean checked);

    // 按店铺全选/取消全选购物车项（勾选同样受可售条件约束）
    int updateCheckedByShop(@Param("userId") Integer userId, @Param("shopId") Integer shopId,
                            @Param("checked") Boolean checked);

    // 将当前用户购物车中不可售商品统一取消选中
    int uncheckUnavailableByUserId(@Param("userId") Integer userId);

    // 批量更新当前用户购物车项选中状态
    int updateCheckedByIds(@Param("userId") Integer userId,
                           @Param("ids") List<Integer> ids,
                           @Param("checked") Boolean checked);

    // 删除单个购物车项
    int deleteById(@Param("id") Integer id);

    // 批量删除购物车项
    int batchDelete(@Param("ids") List<Integer> ids);
}
