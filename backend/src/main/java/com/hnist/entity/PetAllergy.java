package com.hnist.entity;

import lombok.Data;

/** pet_allergies 表行映射：宠物结构化过敏原。 */
@Data
public class PetAllergy {

    private Long id;

    private Integer petId;

    /** 过敏原编码，pet_id 与 allergen_code 组合唯一 */
    private String allergenCode;

    /** 严重程度，默认 unknown */
    private String severity;

    private String note;
}
