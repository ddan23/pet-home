package com.hnist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 商家旗舰店 / 服务门店的创建与更新入参：字段为 null 视为本次不修改，避免部分提交清空已有资料。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMerchantShopRequest {

    /** 通用更新接口据此定位店铺，为空直接报错；路径已带 id 的接口不读该字段。 */
    private Integer id;

    @NotBlank(message = "店铺名称不能为空")
    @Size(max = 100, message = "店铺名称不能超过100个字符")
    private String name;

    /** mall（商城旗舰店）或 service（服务门店），只在创建分流时使用，未知值直接拒收。 */
    private String type;

    @Size(max = 32, message = "门店编号不能超过32个字符")
    private String shopCode;

    private String logo;

    private String description;

    private String address;

    private String businessHours;

    @Size(max = 20, message = "联系电话不能超过20个字符")
    private String contactPhone;
}
