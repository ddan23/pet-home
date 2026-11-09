package com.hnist.common.constant;

/** 店铺类型，落 shops.type；mall 是商家唯一的旗舰店，service 是挂在其下的线下门店。 */
public enum ShopType {

    /** 商城旗舰店：每个商家一个，商品与商城订单都挂在它名下。 */
    MALL("mall"),
    /** 线下服务门店：挂在旗舰店下，承载服务项目、排班与预约。 */
    SERVICE("service");

    private final String code;

    ShopType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
