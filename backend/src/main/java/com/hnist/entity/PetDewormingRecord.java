package com.hnist.entity;

import lombok.Data;

import java.time.LocalDate;

/** pet_deworming_records 表行映射：宠物驱虫记录。 */
@Data
public class PetDewormingRecord {

    private Long id;
    private Integer petId;
    private String productName;
    private LocalDate treatedOn;
    /** 下次驱虫日期，到期提醒按该列筛选 */
    private LocalDate nextDueOn;
    private String note;
}
