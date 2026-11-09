package com.hnist.config;

import com.hnist.security.CurrentUserArgumentResolver;
import com.hnist.security.McpApiKeyInterceptor;
import com.hnist.security.TokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Web MVC 装配：JWT 拦截器、@CurrentUser 参数解析器与上传目录静态映射三件事。 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TokenInterceptor tokenInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final UploadProperties uploadProperties;
    private final McpApiKeyInterceptor mcpApiKeyInterceptor;

    public WebMvcConfig(TokenInterceptor tokenInterceptor,
                        CurrentUserArgumentResolver currentUserArgumentResolver,
                        UploadProperties uploadProperties,
                        McpApiKeyInterceptor mcpApiKeyInterceptor) {
        this.tokenInterceptor = tokenInterceptor;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
        this.uploadProperties = uploadProperties;
        this.mcpApiKeyInterceptor = mcpApiKeyInterceptor;
    }

    /** 拦截器挂 /**：先无条件解析 token 并把用户放进 request 属性，是否强制登录/角色由 @RequireLogin/@RequireRole 注解决定，未标注的接口放行。 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/**");
        // MCP 端点由 starter 自动注册、无 @RequireLogin 可挂，鉴权由独立 API Key 拦截器兜底；
        // 注册在 tokenInterceptor 之后，保证登录态解析仍先执行
        registry.addInterceptor(mcpApiKeyInterceptor)
                .addPathPatterns("/sse", "/mcp/message");
    }

    /** 注册 @CurrentUser 解析器，控制器直接拿到拦截器解析好的用户，避免每个接口手写取值。 */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    /** 把 app.upload.root 映射到 /uploads/**；normalize 后拼 URI 防目录穿越，图片带 30 天 immutable 强缓存，文件名变更才换 URL。 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadRoot = Path.of(uploadProperties.getRoot()).toAbsolutePath().normalize()
                .toUri().toString();
        if (!uploadRoot.endsWith("/")) {
            uploadRoot = uploadRoot + "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadRoot)
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic().immutable());
    }
}
