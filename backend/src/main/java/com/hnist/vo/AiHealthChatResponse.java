package com.hnist.vo;

import com.hnist.domain.HealthCitation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 健康助手问答接口的返回体：每次回答要么带 citations 的正常结果，要么是拒答兜底话术。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiHealthChatResponse {

    private String answer;

    /** true 表示未真正作答：越界、检索空、回答无 [S1] 编号、主题复检失败都会置为 true 并换成兜底文案。 */
    private Boolean refused;

    /** true 时前端必须优先展示就诊提示，与 refused 互相独立：拒答也可能是紧急情况。 */
    private Boolean emergency;

    /** 分类标记，拒答时用于区分 OUT_OF_SCOPE、INSUFFICIENT_EVIDENCE、CITATION_REQUIRED 等成因。 */
    private String category;

    /** 固定免责声明，随回答一起下发，避免前端各自硬编码。 */
    private String disclaimer;

    /** 证据来源，取自知识库分块的文章元数据并按 articleId 去重编号，不是模型生成的链接。 */
    private List<HealthCitation> citations;

    public AiHealthChatResponse(String answer, Boolean refused, Boolean emergency,
                                String category, String disclaimer) {
        this(answer, refused, emergency, category, disclaimer, List.of());
    }
}
