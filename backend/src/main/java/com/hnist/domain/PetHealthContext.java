package com.hnist.domain;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 发送给健康助手的宠物健康上下文，不包含账号和联系方式。 */
@Data
public class PetHealthContext {

    @Size(max = 50)
    private String name;

    @Size(max = 20)
    private String species;

    @Size(max = 50)
    private String breed;

    @Size(max = 10)
    private String gender;

    /** 出生日期字符串，原样取自档案，模型侧据此推算月龄。 */
    @Size(max = 20)
    private String birthDate;

    private Boolean neutered;

    /** 当前体重，单位 kg，与体重记录同单位。 */
    private BigDecimal weight;

    /** 驱虫到期日字符串；当前无赋值点，恒为 null。 */
    @Size(max = 20)
    private String nextDewormingDate;

    @Size(max = 1000)
    private String medicalHistory;

    /** 过敏原编码列表，最多 20 个；推荐与回答都要据此排除条目。 */
    @Size(max = 20, message = "过敏原不能超过20个")
    private List<@Size(max = 50, message = "单个过敏原不能超过50个字符") String> allergies;

    /** 体重记录块，只带上限内最近若干条；条数上限与装配器实际截取长度并不一致。 */
    @Size(max = 12)
    private List<Map<String, Object>> weightRecords;

    @Size(max = 20)
    private List<Map<String, Object>> vaccines;

    @Size(max = 20)
    private List<Map<String, Object>> dewormings;

    @Size(max = 10)
    private List<Map<String, Object>> carePlans;
}
