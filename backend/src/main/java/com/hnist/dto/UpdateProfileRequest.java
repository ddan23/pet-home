package com.hnist.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 个人资料更新入参（PUT /api/user/profile）：昵称必填，其余字段留空表示不填。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称不能超过50个字符")
    private String nickname;

    // 允许空串表示清空手机号，非空时必须是 11 位大陆手机号。
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Size(max = 20, message = "手机号不能超过20个字符")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱不能超过100个字符")
    private String email;

    @Size(max = 50, message = "城市不能超过50个字符")
    private String city;
}
