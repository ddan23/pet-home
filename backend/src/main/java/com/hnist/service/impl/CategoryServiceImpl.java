package com.hnist.service.impl;

import com.hnist.common.constant.ProductType;
import com.hnist.common.exception.BusinessException;
import com.hnist.vo.CategoryResponse;
import com.hnist.mapper.ProductCategoryMapper;
import com.hnist.entity.ProductCategory;
import com.hnist.service.CategoryService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 按商品类型输出分类树，并为商品发布校验可挂载的分类。 */
@Service
public class CategoryServiceImpl implements CategoryService {

    private final ProductCategoryMapper categoryMapper;

    public CategoryServiceImpl(ProductCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Override
    // 分类树读多写极少（无管理端写接口），10 分钟 TTL 兜底即可，key 含 includeEmpty：两种口径的剪枝结果必须分开缓存。
    @Cacheable(cacheNames = "categoryTree", key = "#productType + ':' + #includeEmpty")
    public List<CategoryResponse> getCategoryTree(String productType,
                                                  boolean includeEmpty) {
        String safeType = normalizeProductType(productType);
        // 只取 enabled=1 的分类；item_count 只统计上架商品，且要求店铺 open、商家账号 normal，停业店铺的在售商品不计入。
        List<ProductCategory> categories = categoryMapper.selectEnabledByProductType(safeType);
        // 先用 LinkedHashMap 按 SQL 的 category_level/sort_order 顺序建好全部节点，后续挂父子与最终顺序无关。
        Map<Long, CategoryResponse> nodes =
                new LinkedHashMap<>((int) (categories.size() / 0.75f) + 1);
        for (ProductCategory category : categories) {
            nodes.put(category.getId(), toResponse(category));
        }
        List<CategoryResponse> roots = new ArrayList<>();
        for (ProductCategory category : categories) {
            CategoryResponse node = nodes.get(category.getId());
            if (category.getParentId() == null) {
                roots.add(node);
            } else {
                CategoryResponse parent = nodes.get(category.getParentId());
                // 父分类被停用后不会被查出，这里的子节点拿不到父节点就直接丢弃：否则分类树会出现挂不上去的孤儿节点。
                if (parent != null) {
                    parent.getChildren().add(node);
                }
            }
        }
        // includeEmpty=false 必须先剪空子分类再汇总父级数量：顺序颠倒会让「只剩空子分类」的一级分类被留下来。
        if (!includeEmpty) {
            for (CategoryResponse root : roots) {
                root.getChildren().removeIf(child -> child.getItemCount() == null
                        || child.getItemCount() == 0);
            }
        }
        // 商品只能挂二级分类（requireAssignableCategory 限定 level=2），一级分类自身计数恒为 0，必须累加子分类，否则一级分类会被当成空分类过滤掉。
        for (CategoryResponse root : roots) {
            int childCount = root.getChildren().stream()
                    .mapToInt(child -> child.getItemCount() == null
                            ? 0 : child.getItemCount())
                    .sum();
            root.setItemCount((root.getItemCount() == null ? 0 : root.getItemCount())
                    + childCount);
        }
        if (!includeEmpty) {
            roots.removeIf(root -> root.getItemCount() == null || root.getItemCount() == 0);
        }
        return roots;
    }

    @Override
    public ProductCategory requireAssignableCategory(Long categoryId, String productType) {
        // 分类不做物理删除：products.fk_products_category 是 ON DELETE RESTRICT，被商品引用的分类删不掉，下线手段只能是 enabled=0。
        ProductCategory category = categoryMapper.selectById(categoryId);
        // 停用与不存在同样返回 404：停用分类不应再接收新商品，历史商品仍保留原 category_id 用于展示。
        if (category == null || !Boolean.TRUE.equals(category.getEnabled())) {
            throw new BusinessException(404, "分类不存在或已停用");
        }
        // 类型必须一致：goods 商品挂到 service 分类后，分类树的 type 过滤与商品列表的 product_type 条件会互相矛盾。
        if (!productType.equals(category.getProductType())) {
            throw new BusinessException("分类与发布类型不匹配");
        }
        // 只允许二级分类：商品按 category_id 关联分类树，挂到一级分类会让该分类的汇总计数与实际商品对不上。
        if (!Integer.valueOf(2).equals(category.getCategoryLevel())) {
            throw new BusinessException("请选择具体的二级分类");
        }
        return category;
    }

    private CategoryResponse toResponse(ProductCategory category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setCode(category.getCategoryCode());
        response.setName(category.getCategoryName());
        response.setLevel(category.getCategoryLevel());
        response.setSortOrder(category.getSortOrder());
        response.setMedical(Boolean.TRUE.equals(category.getMedicalFlag()));
        response.setItemCount(category.getItemCount() == null ? 0 : category.getItemCount());
        return response;
    }

    private String normalizeProductType(String productType) {
        // 只放行 goods/service：product_categories.product_type 是 enum，其他值查不出任何分类，且与「该类型暂无分类」无法区分。
        if (ProductType.GOODS.getCode().equals(productType)
                || ProductType.SERVICE.getCode().equals(productType)) {
            return productType;
        }
        throw new BusinessException("产品类型只能是goods或service");
    }
}
