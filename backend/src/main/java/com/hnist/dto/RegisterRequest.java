package com.hnist.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 用户注册入参（POST /api/v1/auth/register）：注册成功即签发登录态，只允许注册 user / business 角色。 */
@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须为3到50个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度必须为8到64个字符")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称不能超过50个字符")
    private String nickname;

    @NotBlank(message = "注册角色不能为空")
    @Pattern(regexp = "user|business", message = "只能注册普通用户或商家账号")
    private String role;

    @Size(max = 20, message = "手机号不能超过20个字符")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱不能超过100个字符")
    private String email;

    @Size(max = 50, message = "城市不能超过50个字符")
    private String city;
}
