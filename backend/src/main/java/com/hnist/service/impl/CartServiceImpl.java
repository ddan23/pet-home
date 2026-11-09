package com.hnist.service.impl;

import com.hnist.common.exception.BusinessException;
import com.hnist.common.constant.ProductStatus;
import com.hnist.common.constant.ProductType;
import com.hnist.mapper.CartMapper;
import com.hnist.mapper.ProductMapper;
import com.hnist.entity.CartItem;
import com.hnist.entity.Product;
import com.hnist.service.CartService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 购物车条目的维护与结算前的可售性校验。 */
@Service
public class CartServiceImpl implements CartService {

    private final CartMapper cartMapper;
    private final ProductMapper productMapper;

    public CartServiceImpl(CartMapper cartMapper, ProductMapper productMapper) {
        this.cartMapper = cartMapper;
        this.productMapper = productMapper;
    }

    @Override
    public List<CartItem> listCartItems(Integer userId) {
        // checked 由 selectByUserId 的 SQL 现算：商品下架、缺货或店铺停业时强制返回 0 并带 unavailableReason，不信任库里的历史勾选值。
        return cartMapper.selectByUserId(userId);
    }

    @Override
    public int countCartItems(Integer userId) {
        // 购物车角标显示的是商品总件数，所以对 quantity 求和而不是统计行数。
        return cartMapper.countQuantityByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addToCart(Integer userId, Integer productId, Integer quantity) {
        // 一次加购至少 1 件：数量直接进 quantity 列参与后续金额计算，0 或负数会让订单金额与库存扣减同时错乱。
        if (quantity == null || quantity < 1) {
            throw new BusinessException("商品数量不能小于1");
        }
        // 购物车只收 goods：selectPublicById 已在 SQL 内限定 status='on' 与 product_type，service 走预约下单，不能加进购物车。
        Product product = productMapper.selectPublicById(
                productId, ProductType.GOODS.getCode());
        if (product == null
                || !ProductStatus.ON.getCode().equals(product.getStatus())
                || !ProductType.GOODS.getCode().equals(product.getProductType())) {
            throw new BusinessException("商品不存在或已下架");
        }
        // 同一用户同一商品在 cart_items 上由唯一键 uk_user_product(user_id, product_id) 保证只有一行，因此先查再决定插入还是更新。
        CartItem existing = cartMapper.selectByUserAndProduct(userId, productId);
        // 重复加购按累加处理：用户再次点「加入购物车」的语义是在原数量上追加，而不是覆盖成这一次的件数。
        int targetQuantity = quantity + (existing == null ? 0 : existing.getQuantity());
        validateStock(product, targetQuantity);
        if (existing == null) {
            CartItem item = new CartItem();
            item.setUserId(userId);
            item.setProductId(productId);
            item.setQuantity(targetQuantity);
            item.setChecked(true);
            cartMapper.insert(item);
            return;
        }
        cartMapper.updateQuantity(existing.getId(), targetQuantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuantity(Integer cartId, Integer userId, Integer quantity) {
        CartItem item = requireOwnedItem(cartId, userId);
        Product product = productMapper.selectPublicById(
                item.getProductId(), ProductType.GOODS.getCode());
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        validateStock(product, quantity);
        // 改数量是覆盖语义：用户直接在数字框里给出目标件数，不再与旧值叠加。
        cartMapper.updateQuantity(cartId, quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleChecked(Integer cartId, Integer userId) {
        CartItem item = requireOwnedItem(cartId, userId);
        boolean nextChecked = !Boolean.TRUE.equals(item.getChecked());
        // 只在「勾上」时校验可售：取消勾选必须永远放行，否则用户无法手动把失效商品从结算列表里摘掉。
        if (nextChecked) {
            Product product = productMapper.selectPublicById(
                    item.getProductId(), ProductType.GOODS.getCode());
            if (product == null
                    || !ProductStatus.ON.getCode().equals(product.getStatus())) {
                throw new BusinessException("商品已下架，不能选择结算");
            }
        }
        cartMapper.updateChecked(cartId, nextChecked);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeItem(Integer cartId, Integer userId) {
        requireOwnedItem(cartId, userId);
        cartMapper.deleteById(cartId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkAll(Integer userId, boolean checked) {
        // 全选不能简单置 1：updateCheckedByUserId 的 CASE WHEN 只对可用商品置 1，其余强制写 0，避免失效商品被带进结算。
        cartMapper.updateCheckedByUserId(userId, checked);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkByShop(Integer userId, Integer shopId, boolean checked) {
        // 按店铺勾选只作用于该店商品，SQL 用 p.shop_id 限定范围，跨店条目的勾选状态保持不变。
        cartMapper.updateCheckedByShop(userId, shopId, checked);
    }

    private CartItem requireOwnedItem(Integer cartId, Integer userId) {
        // 越权与不存在统一返回 404：购物车 id 自增可枚举，返回 403 就等于确认该行属于别人。
        CartItem item = cartMapper.selectById(cartId);
        if (item == null || !userId.equals(item.getUserId())) {
            throw new BusinessException(404, "购物车项不存在");
        }
        return item;
    }

    private void validateStock(Product product, int quantity) {
        if (quantity < 1) {
            throw new BusinessException("商品数量不能小于1");
        }
        // 购物车不占用库存，这里只做提前提示；真正能否扣到由下单时 reserveStock 的条件更新裁决，并发下以影响行数为准。
        if (product.getStock() != null && product.getStock() < quantity) {
            throw new BusinessException("库存不足");
        }
    }
}
