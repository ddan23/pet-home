package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 点赞切换结果：点赞与取消共用。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LikeToggleResponse {
    /** 操作后当前用户是否已点赞。 */
    private Boolean liked;

    /** 点赞数，为本次读取快照 ±1 的即时值，仅供展示；权威值以 SQL 原子加减后的行为准。 */
    private Integer likes;
}
