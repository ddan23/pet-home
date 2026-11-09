package com.hnist.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** pets 表行映射：用户宠物档案，查询只取 status 为 normal 且 deleted_at 为空的行。 */
@Data
public class Pet {

    private Integer id;
    private Integer userId;
    private String name;
    private String species;
    private String breed;
    private String gender;
    private String color;
    private LocalDate birthDate;
    private Boolean neutered;
    /** 体重（kg），decimal(6,2)；null 表示未称重 */
    private BigDecimal weight;
    private String photoUrl;
    private String medicalNotes;
    /** 新增固定写 normal，注销走 deleted_at 软删（该列未映射到本类） */
    private String status;
    private LocalDateTime createdAt;
}
