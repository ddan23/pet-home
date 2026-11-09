package com.hnist.vo;

import com.hnist.entity.Shop;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 商家门店组织结构：一家旗舰店 + 若干服务门店。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantOrganizationResponse {

    /** 旗舰店，商家尚未建店时为 null，组织页需按空态渲染。 */
    private Shop flagship;

    private List<Shop> serviceOutlets;

    /** 服务门店数量，即 serviceOutlets.size()，避免前端再算一次。 */
    private Integer serviceOutletCount;
}
