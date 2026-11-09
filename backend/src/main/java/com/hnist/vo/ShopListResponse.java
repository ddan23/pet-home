package com.hnist.vo;

import com.hnist.entity.Shop;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/** 商家自有门店列表：不分页，按 ownerId 收窄后整份返回。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopListResponse {
    private List<Shop> list;
    /** 条数，即 list.size()，前端无需再算。 */
    private Integer total;
}
