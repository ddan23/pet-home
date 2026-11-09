package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 登录/注册/校验登录态的返回体：只回身份信息，凭据通过 HttpOnly Cookie 下发。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenInfo {
    private Integer id;
    /** 恒为 null：JWT 写进 HttpOnly Cookie，不回传响应体，避免被前端脚本读取。 */
    private String token;
    private String role;
    private String nickname;
    private String avatar;
}
