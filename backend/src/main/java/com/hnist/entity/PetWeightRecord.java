package com.hnist.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** pet_weight_records 表行映射：宠物体重记录。 */
@Data
public class PetWeightRecord {

    private Long id;
    private Integer petId;
    /** 体重（kg），decimal(6,2) */
    private BigDecimal weight;
    private LocalDate recordedOn;
    private String note;
}
