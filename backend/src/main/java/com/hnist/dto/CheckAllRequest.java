package com.hnist.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 购物车批量勾选入参：全选与按店铺勾选共用，checked 决定把命中的购物车行置为勾选还是取消。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckAllRequest {

    @NotNull(message = "选中状态不能为空")
    private Boolean checked;
}
