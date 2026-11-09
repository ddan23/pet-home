package com.hnist.service;

import com.hnist.vo.CategoryResponse;
import com.hnist.entity.ProductCategory;

import java.util.List;

/** 分类树查询与商品发布时的分类校验。 */
public interface CategoryService {

    // 只返回启用分类；includeEmpty=false 时剪掉没有在售商品的分类。
    List<CategoryResponse> getCategoryTree(String productType, boolean includeEmpty);

    // 分类必须存在、启用、类型匹配且为二级分类，不满足时抛业务异常。
    ProductCategory requireAssignableCategory(Long categoryId, String productType);
}
