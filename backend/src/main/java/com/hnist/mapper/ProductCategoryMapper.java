package com.hnist.mapper;

import com.hnist.entity.ProductCategory;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 商品与服务分类数据访问。 */
public interface ProductCategoryMapper {

    /** item_count 只统计上架（status = 'on'）且店铺 open、未归档、商家账号 normal 的商品，禁用分类不返回。 */
    List<ProductCategory> selectEnabledByProductType(
            @Param("productType") String productType);

    ProductCategory selectById(@Param("id") Long id);
}
