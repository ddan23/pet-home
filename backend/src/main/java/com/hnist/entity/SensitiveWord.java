package com.hnist.entity;

import lombok.Data;

/** sensitive_words 表行映射：DFA 敏感词规则。 */
@Data
public class SensitiveWord {

    private Integer id;
    private String word;
    private String category;
    /** REVIEW 或 BLOCK，多条命中时取权重最高的一条 */
    private String severity;
    private Boolean enabled;
}
