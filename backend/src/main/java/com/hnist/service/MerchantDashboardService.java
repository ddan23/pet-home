package com.hnist.service;

import com.hnist.vo.MerchantDashboardOverview;

/** 商家经营总览服务。 */
public interface MerchantDashboardService {

    // 只汇总该 ownerId 名下营业中的门店，营业额与交易数只计 completed 订单；非营业门店仍会出现在 shops 列表里但不进 summary。
    MerchantDashboardOverview getOverview(Integer ownerId);
}
