package com.hnist.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 绑定 app.health-report.*，目前只有 PDF 生成用的中文字体路径；属性缺失不影响启动，生成报告时才消费。 */
@Data
@ConfigurationProperties(prefix = "app.health-report")
public class HealthReportPdfProperties {

    /** 字体文件路径（HEALTH_REPORT_FONT_PATH，默认空）：为空时回退到包内 MaShanZheng 字体；配置了但文件不存在则生成报错。 */
    private String fontPath;
}
