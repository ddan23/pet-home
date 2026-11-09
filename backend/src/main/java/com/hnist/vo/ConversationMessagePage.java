package com.hnist.vo;

import com.hnist.entity.Message;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 会话消息游标分页结果：以消息 id 为游标，比 offset 翻页更稳定。 */
@Data
@AllArgsConstructor
public class ConversationMessagePage {

    /** 已翻转为时间正序，前端可直接追加渲染；SQL 侧实际按 id 倒序取最近 N 条。 */
    private List<Message> messages;

    /** 由“多取一条”探测得出，探测行本身不返回。 */
    private Boolean hasMore;

    /** 下一页游标：本页最早一条的 id；本页为空时回传请求传入的 beforeId。 */
    private Integer nextBeforeId;
}
