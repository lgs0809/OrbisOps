package cn.lgs.orbisops.trigger.application.resourcehealth;

import cn.lgs.orbisops.application.config.McpClientCatalogPort;
import cn.lgs.orbisops.application.resourcehealth.McpRegistryResourceHealthProbePort;
import cn.lgs.orbisops.application.resourcehealth.ResourceHealthCheck;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Trigger adapter exposing the legacy MCP registry repository through an application port. */
@Component
public class OpsMcpRegistryResourceHealthAdapter
        implements McpRegistryResourceHealthProbePort {

    private final McpClientCatalogPort repository;

    public OpsMcpRegistryResourceHealthAdapter(
            ObjectProvider<McpClientCatalogPort> repositoryProvider) {
        this.repository = repositoryProvider.getIfAvailable();
    }

    @Override
    public ResourceHealthCheck probe() {
        if (repository == null) {
            return ResourceHealthCheck.unavailable(
                    "mcp",
                    "MCP 配置库",
                    "ai_client_tool_mcp",
                    "MCP DAO 未初始化");
        }
        try {
            int enabled = repository.listEnabled().size();
            return new ResourceHealthCheck(
                    "mcp",
                    "MCP 配置库",
                    "ai_client_tool_mcp",
                    true,
                    "启用 MCP 配置 " + enabled + " 个",
                    Map.of("enabledCount", enabled));
        } catch (RuntimeException error) {
            String message = error.getMessage();
            return ResourceHealthCheck.unavailable(
                    "mcp",
                    "MCP 配置库",
                    "ai_client_tool_mcp",
                    message == null || message.isBlank()
                            ? error.getClass().getSimpleName()
                            : message.trim());
        }
    }
}
