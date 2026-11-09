package com.hnist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 地址簿整份保存入参（PUT /api/user/addresses）：addresses 是 JSON 数组字符串，落库为整份覆盖。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveAddressesRequest {

    // 前端提交的完整地址数组（收货人、手机号、省市区、详细地址），校验不通过则整体不入库。
    @NotBlank(message = "地址信息不能为空")
    @Size(max = 50000, message = "地址数据过大")
    private String addresses;
}
