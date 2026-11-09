package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户地址簿接口（`/user/addresses`）的返回体：整份地址以 JSON 数组字符串收发，不在服务端拆成结构化字段。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressesResponse {
    /** JSON 数组字符串；账号从未保存过地址时服务层回填 `[]`，前端可直接按数组解析。 */
    private String addresses;
}
