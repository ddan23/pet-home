package com.hnist.common.constant;

/** 商品类型，落 products.product_type；goods 是实物，service 是可预约的线下服务。 */
public enum ProductType {

    /** 实物商品：参与库存扣减，库存为 0 时自动下架。 */
    GOODS("goods"),
    /** 服务项目：不占实物库存，通过排班时段提供预约。 */
    SERVICE("service");

    private final String code;

    ProductType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
