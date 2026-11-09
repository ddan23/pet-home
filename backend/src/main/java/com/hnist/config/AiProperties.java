package com.hnist.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 绑定 app.ai.*：AI 业务开关与调用配额。模型连接参数已迁移到 spring.ai.deepseek.*（Spring AI 官方配置节）。 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** 总开关 app.ai.enabled，默认 true；关闭后等同于未配置，所有 AI 调用都不发起。 */
    private boolean enabled = true;

    /** 单次生成的 token 上限，默认 2048；与请求级 maxTokens 取较小者生效，防止个别场景撑爆预算。 */
    private int maxTokens = 2048;

    /** 拼进上下文的历史消息条数上限，默认 12，超出部分截断以免长会话撑爆 token。 */
    private int maxHistoryMessages = 12;

    /** 单用户每日调用上限，默认 20；达到后抛 429，计数按 Asia/Shanghai 自然日重置。 */
    private int dailyLimit = 20;

    /** 单用户每分钟调用上限，默认 5；用于挡住短时间连续点击，超出抛 429。 */
    private int minuteLimit = 5;
}
