package com.hnist.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 通用状态变更入参：帖子 / 评论审核、商品上下架、店铺营业状态共用同一个请求体。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStatusRequest {

    // 允许值由各接口的白名单决定（商品 on/off、评论 normal/blocked、店铺 open/closed），未知值一律拒收。
    @NotBlank(message = "状态不能为空")
    private String status;
}
