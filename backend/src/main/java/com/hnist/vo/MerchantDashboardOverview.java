package com.hnist.vo;

import com.hnist.entity.Order;
import com.hnist.entity.Product;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 商家经营总览聚合数据：门店全量列出，但只有 status=open 的门店参与汇总与待办统计。 */
@Data
@NoArgsConstructor
public class MerchantDashboardOverview {

    private Summary summary = new Summary();

    /** 全部自有门店（含待审核、驳回、停业），仅用于列表展示。 */
    private List<ShopOverview> shops = new ArrayList<>();

    /** 库存 ≤ 10 的商品，按库存升序、上架时间倒序，最多 5 条。 */
    private List<Product> lowStockProducts = new ArrayList<>();

    /** 各店商品订单合并后按创建时间倒序，最多 6 条。 */
    private List<Order> recentOrders = new ArrayList<>();

    /** 当天场次预约，按 slotDate + startTime 升序，最多 6 条。 */
    private List<Order> todayAppointments = new ArrayList<>();

    /** 全店铺经营汇总。 */
    @Data
    @NoArgsConstructor
    public static class Summary {
        /** 已完成订单金额合计（元），由 SQL 聚合逐店累加，不走应用层遍历分页切片。 */
        private BigDecimal totalRevenue = BigDecimal.ZERO;
        private Integer totalShopCount;
        /** 参与汇总的营业中门店数，其余门店只出现在 shops 列表里。 */
        private Integer activeShopCount;
        private Integer productCount;
        private Integer serviceCount;
        /** 待发货口径是 status=paid 的商品订单，与待付款订单分开统计。 */
        private Integer pendingShipments;
        /** 待服务口径是 status=confirmed 的预约单。 */
        private Integer pendingAppointments;
        /** 计入补货预警的商品数，阈值与列表页 stockMax 过滤同口径。 */
        private Integer lowStockCount;
        /** 交易数：已完成商品订单 + 已完成服务预约 */
        private Integer transactionCount;
    }

    /** 单个店铺的经营摘要。 */
    @Data
    @NoArgsConstructor
    public static class ShopOverview {
        private Integer id;
        private String name;
        private String type;
        private String status;
        private String logo;
        private String address;
        private BigDecimal revenue = BigDecimal.ZERO;
        /** 累计营业数：该店已完成订单/预约数 */
        private Integer transactionCount;
        /** goods 记商品数、service 记服务项数，两者共表按 product_type 区分。 */
        private Integer itemCount;
        /** 商城店为待发货数，服务店为待服务预约数。 */
        private Integer pendingCount;
        /** 仅商城店有值：低库存商品数，服务店恒为 0。 */
        private Integer warningCount;
    }
}
