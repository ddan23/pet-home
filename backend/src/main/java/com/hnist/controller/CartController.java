package com.hnist.controller;

import com.hnist.common.constant.AccountRole;

import com.hnist.annotation.CurrentUser;
import com.hnist.annotation.RequireRole;
import com.hnist.common.result.Result;
import com.hnist.dto.AddToCartRequest;
import com.hnist.dto.CheckAllRequest;
import com.hnist.dto.UpdateQuantityRequest;
import com.hnist.entity.CartItem;
import com.hnist.entity.User;
import com.hnist.service.CartService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 购物车控制器。 */
@RestController
@RequestMapping({"/api/cart", "/api/v1/cart"})
@RequireRole(AccountRole.USER_CODE)
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public Result<List<CartItem>> listCart(@CurrentUser User user) {
        return Result.ok(cartService.listCartItems(user.getId()));
    }

    @GetMapping("/count")
    public Result<Integer> countCartItems(@CurrentUser User user) {
        return Result.ok(cartService.countCartItems(user.getId()));
    }

    @PostMapping
    public Result<Void> addToCart(@Valid @RequestBody AddToCartRequest request,
                                  @CurrentUser User user) {
        cartService.addToCart(user.getId(), request.getProductId(), request.getQuantity());
        return Result.ok("已加入购物车", null);
    }

    @PutMapping("/{id}/quantity")
    public Result<Void> updateQuantity(@PathVariable Integer id,
                                       @Valid @RequestBody UpdateQuantityRequest request,
                                       @CurrentUser User user) {
        cartService.updateQuantity(id, user.getId(), request.getQuantity());
        return Result.ok();
    }

    @PutMapping("/{id}/check")
    public Result<Void> toggleChecked(@PathVariable Integer id, @CurrentUser User user) {
        cartService.toggleChecked(id, user.getId());
        return Result.ok();
    }

    @PutMapping("/checkAll")
    public Result<Void> checkAll(@Valid @RequestBody CheckAllRequest request,
                                 @CurrentUser User user) {
        cartService.checkAll(user.getId(), request.getChecked());
        return Result.ok();
    }

    @PutMapping("/checkByShop/{shopId}")
    public Result<Void> checkByShop(@PathVariable Integer shopId,
                                    @Valid @RequestBody CheckAllRequest request,
                                    @CurrentUser User user) {
        cartService.checkByShop(user.getId(), shopId, request.getChecked());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> removeItem(@PathVariable Integer id, @CurrentUser User user) {
        cartService.removeItem(id, user.getId());
        return Result.ok();
    }
}
