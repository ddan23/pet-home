package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 商家营业额摘要：已完成营业额 + 交易数（已完成订单/预约数）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantRevenueSummary {

    /** 已完成订单金额合计（元），只累加 status=completed，金额按分精确。 */
    private BigDecimal revenue = BigDecimal.ZERO;

    /** 与 revenue 同口径的完成笔数，混入 paid/shipped 会让成交笔数高于实际完成量。 */
    private Integer transactionCount;
}
