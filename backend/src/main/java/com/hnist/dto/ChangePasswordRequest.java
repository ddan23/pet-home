package com.hnist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 修改密码入参（POST /api/v1/auth/password）：需登录态，且必须再验一次原密码。 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "当前密码不能为空")
    private String currentPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "新密码长度必须为8到64个字符")
    private String newPassword;
}
