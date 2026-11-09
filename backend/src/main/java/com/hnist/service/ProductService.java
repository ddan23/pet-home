package com.hnist.service;

import com.hnist.dto.SaveProductRequest;
import com.hnist.common.result.PageResult;
import com.hnist.entity.Product;

/** 商品与服务的查询、发布与上下架契约。 */
public interface ProductService {

    // 商城列表：只含上架 goods 且店铺营业，返回总条数供前端分页。
    PageResult<Product> getUserProducts(String categoryCode, String keyword,
                                        Integer shopId, int page, int pageSize);

    // 服务列表：product_type 固定 service，服务通过预约排班承接，不进购物车。
    PageResult<Product> getUserServices(String categoryCode, String keyword,
                                        Integer shopId, int page, int pageSize);

    // 内部读取，不按状态与类型过滤；对外详情请用 getPublicProductById。
    Product getProductById(Integer id);

    // 对外详情：未上架、类型不符或店铺非营业时返回 null，由调用方转 404。
    Product getPublicProductById(Integer id, String productType);

    // 商家列表按 shopId 收口，status/productType/stockMax 为可选过滤，其中 stockMax 用于补货提醒。
    PageResult<Product> getMerchantProducts(Integer shopId, String keyword,
                                            String categoryCode,
                                            String status, Integer stockMax,
                                            String productType,
                                            int page, int pageSize);

    // 总部视角按 ownerId 跨门店查服务，不受单个 shopId 限制。
    PageResult<Product> getMerchantServicesByOwner(Integer ownerId, String keyword,
                                                    String categoryCode, String status,
                                                    int page, int pageSize);

    // 新增或更新：shopId 来自登录态，更新时校验原商品归属；库存为 0 的 goods 会被置为下架。
    Product saveProduct(Integer shopId, SaveProductRequest request, String defaultProductType);

    // 归属不符或商品不存在均抛 404；切到上架时 goods 必须有正库存。
    void toggleStatus(Integer productId, Integer shopId);

    // 物理删除并校验归属，购物车里的残留行由外键级联清理。
    void deleteProduct(Integer productId, Integer shopId);

    // 管理员全量列表，可按状态与类型收窄，不做店铺隔离。
    PageResult<Product> getAdminProducts(String keyword, String status, String productType, int page, int pageSize);

    // 只接受 on/off；管理员上架同样要通过库存校验。
    void updateProductStatus(Integer productId, String status);

}
