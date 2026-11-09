package com.hnist.mcp;

import com.hnist.service.HealthReminderService;
import com.hnist.service.KnowledgeRetrievalService;
import com.hnist.service.PetHealthQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MCP 工具装配：把 mcp 包下的 @Tool 工具对象注册进 MCP Server（WebMVC / SSE 传输）。 */
@Configuration
public class McpToolConfig {

    @Bean
    public PetHealthTools petHealthTools(PetHealthQueryService petHealthQueryService,
                                         ObjectMapper objectMapper) {
        return new PetHealthTools(petHealthQueryService, objectMapper);
    }

    @Bean
    public KnowledgeTools knowledgeTools(KnowledgeRetrievalService knowledgeRetrievalService,
                                         ObjectMapper objectMapper) {
        return new KnowledgeTools(knowledgeRetrievalService, objectMapper);
    }

    @Bean
    public ReminderTools reminderTools(HealthReminderService healthReminderService,
                                       ObjectMapper objectMapper) {
        return new ReminderTools(healthReminderService, objectMapper);
    }

    // MCP Server 自动配置会收集容器内全部 ToolCallbackProvider，按工具名去重后注册为 MCP 工具
    @Bean
    public ToolCallbackProvider petHomeToolCallbackProvider(PetHealthTools petHealthTools,
                                                            KnowledgeTools knowledgeTools,
                                                            ReminderTools reminderTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(petHealthTools, knowledgeTools, reminderTools)
                .build();
    }
}
