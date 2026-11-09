package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 当前用户点赞过的帖子 id 集合，以 JSON 数组字符串原样透出。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LikedIdsResponse {
    /** users.liked_posts 的 JSON 数组字符串；未登录或从未点赞时返回 `[]`。 */
    private String likedIds;
}
