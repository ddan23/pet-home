package com.hnist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 宠物档案整份保存入参（PUT /api/user/pets）：pets 是 JSON 数组字符串，请求中缺失的宠物按删除处理。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SavePetsRequest {

    // 全量档案数组：带 id 且属于当前用户的按更新处理，不带 id 的新建，未出现的既有宠物删除。
    @NotBlank(message = "宠物信息不能为空")
    @Size(max = 100000, message = "宠物档案数据过大")
    private String pets;
}
