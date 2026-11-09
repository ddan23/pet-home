package com.hnist.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 集中注册配置属性类：@ConfigurationProperties 类不在组件扫描范围，必须在此显式启用才会绑定。 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, UploadProperties.class, AiProperties.class,
        HealthReportPdfProperties.class, McpSecurityProperties.class})
public class ApplicationPropertiesConfig {
}
