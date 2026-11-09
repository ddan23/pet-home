package com.hnist.security;

import com.hnist.common.result.Result;
import com.hnist.config.McpSecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * MCP 端点 API Key 拦截器：为 /sse 与 /mcp/message 提供访问边界。
 * starter 注册的 MCP 端点无注解可挂，TokenInterceptor 对其只解析不拦截，
 * 因此鉴权由此拦截器独立完成，失败以统一 Result JSON 写回 401。
 */
@Component
public class McpApiKeyInterceptor implements HandlerInterceptor {

    private static final String API_KEY_HEADER = "X-MCP-Api-Key";
    private static final String BEARER_PREFIX = "Bearer ";

    private final McpSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public McpApiKeyInterceptor(McpSecurityProperties securityProperties, ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    /** 未开启鉴权直接放行；开启后未携带合法 key 的请求一律 401。 */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!securityProperties.isEnabled()) {
            return true;
        }
        String provided = resolveApiKey(request);
        if (provided != null && matches(provided, securityProperties.getApiKey())) {
            return true;
        }
        writeError(response, 401, "MCP 访问密钥缺失或无效");
        return false;
    }

    // 同时支持 X-MCP-Api-Key 头与 Authorization: Bearer，覆盖常见 MCP 客户端的凭证携带方式
    private String resolveApiKey(HttpServletRequest request) {
        String header = request.getHeader(API_KEY_HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    // 恒定时间比较，避免按字节短路的时序侧信道
    private boolean matches(String provided, String expected) {
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    /** 与 TokenInterceptor 同一错误格式：统一 Result JSON，HTTP 状态码与业务码一致。 */
    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(code, message));
    }
}
