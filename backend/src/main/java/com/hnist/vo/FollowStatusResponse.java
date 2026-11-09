package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 关注状态查询/切换的返回体：关注与取关共用，只看 after 状态。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FollowStatusResponse {
    /** 操作后的关注状态；查询接口返回当前是否为粉丝。 */
    private Boolean following;
}
