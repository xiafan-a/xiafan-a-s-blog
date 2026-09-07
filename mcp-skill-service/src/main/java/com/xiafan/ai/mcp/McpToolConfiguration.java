package com.xiafan.ai.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.xiafan.ai.tool.ToolRegistryService;

@Configuration
public class McpToolConfiguration {

    @Bean
    ToolCallbackProvider skillMcpTools(McpSkillTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    @Bean
    ToolCallbackProvider toolManagementMcpTools(McpToolManagementTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    @Bean
    ToolCallbackProvider agentMcpTools(ToolRegistryService registry) {
        return registry::callbacks;
    }
}
