package com.hnist.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** 预约时段批量生成结果。当前批量生成方法直接返回生成条数，本类尚无调用方引用，仅作结构预留。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentSlotGenerationResponse {

    /** 本次实际插入的时段行数；窗口内已有任意时段时生成为 0，不重复插入。 */
    private Integer generatedCount;

    /** 生成窗口起点，实际批量为本周一至下周日共 14 天。 */
    private LocalDate from;

    /** 生成窗口终点，含当天。 */
    private LocalDate to;
}
