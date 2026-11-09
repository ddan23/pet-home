package com.hnist.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/** 商品与服务发布 / 更新入参（/api/merchant/products 与 /api/merchant/services 共用）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveProductRequest {

    /** 为空表示新建，非空表示更新既有记录。 */
    private Integer id;

    /** 落库值取接口入口传的 goods / service，前端传值不生效；goods 与 service 的必填项校验按它分流。 */
    private String productType;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称不能超过100个字符")
    private String name;

    @Size(max = 5000, message = "描述不能超过5000个字符")
    private String description;

    @Pattern(regexp = "CAT|DOG|ALL", message = "适用宠物只能是CAT、DOG或ALL")
    private String targetPet;

    @Pattern(regexp = "YOUNG|ADULT|SENIOR|ALL",
            message = "适用阶段只能是YOUNG、ADULT、SENIOR或ALL")
    private String lifeStage;

    @Size(max = 500, message = "健康标签不能超过500个字符")
    private String healthTags;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.00", inclusive = false, message = "价格必须大于0")
    private BigDecimal price;

    // 缺省按 0 落库；goods 库存为 0 会被自动置为下架，service 不校验库存。
    @Min(value = 0, message = "库存不能小于0")
    private Integer stock;

    @Size(max = 20, message = "时长不能超过20个字符")
    private String duration;

    /** 服务时长分钟数：service 类型必填，goods 可空；范围校验由服务层按类型分流兜底 */
    @Min(value = 15, message = "服务时长不能少于15分钟")
    @Max(value = 480, message = "服务时长不能超过480分钟")
    private Integer durationMinutes;

    @Size(max = 255, message = "图片地址过长")
    private String imageUrl;
}
