package com.hnist.entity;

import lombok.Data;

import java.time.LocalDate;

/** pet_vaccinations 表行映射：宠物疫苗记录。 */
@Data
public class PetVaccination {

    private Long id;
    private Integer petId;
    private String vaccineName;
    private LocalDate vaccinatedOn;
    /** 下次接种日期，到期提醒按该列筛选 */
    private LocalDate nextDueOn;
    private String batchNo;
    private String vetName;
}
