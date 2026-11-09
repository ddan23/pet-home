package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户主页对外资料：只含 id、昵称、头像、角色与店铺展示字段，手机号、邮箱、地址不进入本结构。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPublicInfoResponse {

    private Integer id;

    private String nickname;

    private String avatar;

    /** 账号角色，决定主页展示买家还是商家信息。 */
    private String role;

    /** 商家账号的店铺标识，普通用户为 null。 */
    private String shopLogo;

    private String shopName;
}
