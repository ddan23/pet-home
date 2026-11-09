package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 宠物档案接口的返回体：整份档案以 JSON 数组字符串收发，服务端不拆字段。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetsResponse {
    /** pets 表读出的 JSON 数组字符串，与写入接口的入参格式一致，可整份回传覆盖。 */
    private String pets;
}
