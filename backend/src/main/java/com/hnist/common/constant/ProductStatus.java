package com.hnist.common.constant;

/** 商品上下架状态，落 products.status；只有 on 的商品能被加购、下单或预约。 */
public enum ProductStatus {

    /** 上架：创建/更新且库存充足时写入，对外可售。 */
    ON("on"),
    /** 下架：手动下架或实物商品库存归零时写入，对外入口统一拒绝。 */
    OFF("off");

    private final String code;

    ProductStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
