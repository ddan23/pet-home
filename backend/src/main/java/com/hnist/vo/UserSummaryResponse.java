package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 关注/粉丝列表项：由 user_follows 联查 users 投影，只带展示所需的最小字段。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryResponse {

    private Integer id;

    private String nickname;

    private String avatar;
}
