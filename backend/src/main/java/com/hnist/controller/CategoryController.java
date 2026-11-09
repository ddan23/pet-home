package com.hnist.controller;

import com.hnist.common.result.Result;
import com.hnist.vo.CategoryResponse;
import com.hnist.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 商品和服务分类控制器。 */
@RestController
@RequestMapping({"/api/categories", "/api/v1/categories"})
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public Result<List<CategoryResponse>> listCategories(
            @RequestParam(name = "productType") String productType,
            @RequestParam(name = "includeEmpty", defaultValue = "false")
            boolean includeEmpty) {
        return Result.ok(categoryService.getCategoryTree(productType, includeEmpty));
    }
}
