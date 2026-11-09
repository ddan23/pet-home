package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 站内通知的分类未读数，一行对应一个 NotificationCategory。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCategoryCount {

    /** 分类编码：system、community、mall、service、health；SQL 只返回有未读的分类，其余由服务层预置 0。 */
    private String category;

    private Integer unreadCount;
}
