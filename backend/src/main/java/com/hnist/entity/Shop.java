package com.hnist.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** shops 表行映射：商城（mall）与服务（service）两类店铺，flagshipId 为空表示旗舰店本身。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Shop implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer ownerId;
    /** 所属旗舰店ID；旗舰店自身为空 */
    private Integer flagshipId;
    private String name;
    private String shopCode;
    /** mall, service */
    private String type;
    /** open, closed, rejected, pending_audit */
    private String status;
    private String logo;
    private String description;
    /** 营业时间 */
    private String businessHours;
    private String address;
    /** 累计销售额，decimal(10,2) */
    private BigDecimal totalSales;
    /** 店铺综合评分，decimal(2,1)，范围 0.0-5.0 */
    private BigDecimal rating;
    private LocalDateTime createdAt;
    private LocalDateTime archivedAt;
    /** 非DB字段，关联 users 带出的商家昵称 */
    private String merchantName;
    private String contactPhone;
}
