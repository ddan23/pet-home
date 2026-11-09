package com.hnist.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 后台社区帖子统计：一次 GROUP BY status 聚合后分发到各字段。 */
@Data
public class PostStatsResponse {

    private Integer normal;

    private Integer pending;

    private Integer blocked;

    /** 四个状态计数之和，随聚合顺带累加，不另查总数。 */
    private Integer total;

    /** 今日发帖数，用数据库 DATE(created_at) = CURDATE() 判定，跨时区部署时以数据库时区为准。 */
    @JsonProperty("today")
    private Integer todayCount;
}
