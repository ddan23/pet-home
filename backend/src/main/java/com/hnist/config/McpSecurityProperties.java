package com.hnist.config;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 绑定 app.mcp.security.*：MCP 端点（/sse、/mcp/message）的 API Key 访问边界配置。 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.mcp.security")
public class McpSecurityProperties {

    /** 是否开启 MCP 端点鉴权（MCP_SECURITY_ENABLED）：开发期默认关闭便于 Inspector 直连；生产必须开启。 */
    private boolean enabled = false;

    /** 访问令牌（MCP_API_KEY）：开启后 MCP 客户端必须携带 X-MCP-Api-Key 或 Authorization: Bearer。 */
    private String apiKey;

    /** 开启鉴权但未配 key 会启动期失败，避免部署后端点变成“谁也进不去”或“谁都能进”。 */
    @AssertTrue(message = "MCP 鉴权开启时必须配置 app.mcp.security.api-key，且长度不少于16个字符")
    private boolean isKeyConfiguredWhenEnabled() {
        return !enabled || (apiKey != null && apiKey.length() >= 16);
    }
}
