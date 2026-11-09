package com.hnist.controller;

import com.hnist.common.constant.AccountRole;

import com.hnist.annotation.CurrentUser;
import com.hnist.annotation.RequireRole;
import com.hnist.common.result.Result;
import com.hnist.vo.MerchantDashboardOverview;
import com.hnist.entity.User;
import com.hnist.service.MerchantDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商家经营总览控制器。 */
@RestController
@RequestMapping({"/api", "/api/v1"})
public class MerchantDashboardController {

    private final MerchantDashboardService merchantDashboardService;

    public MerchantDashboardController(MerchantDashboardService merchantDashboardService) {
        this.merchantDashboardService = merchantDashboardService;
    }

    @GetMapping("/merchant/dashboard")
    @RequireRole(AccountRole.BUSINESS_CODE)
    public Result<MerchantDashboardOverview> getOverview(@CurrentUser User user) {
        return Result.ok(merchantDashboardService.getOverview(user.getId()));
    }
}
