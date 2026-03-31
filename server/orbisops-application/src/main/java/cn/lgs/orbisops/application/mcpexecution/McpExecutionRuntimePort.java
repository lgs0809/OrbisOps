package cn.lgs.orbisops.application.mcpexecution;

import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionConfig;
import cn.lgs.orbisops.domain.mcpexecution.model.McpExecutionRequest;

import java.util.Map;

public interface McpExecutionRuntimePort {

    McpRuntimeCatalog catalog(McpExecutionConfig config);

    McpRuntimeActivation activate(
            McpExecutionRequest request,
            String toolName,
            String reason,
            Map<String, Object> remoteDefinition);

    McpRuntimeToolSchema schema(McpExecutionRequest request, String toolName);

    McpRuntimeToolSchema hydrate(
            McpExecutionRequest request,
            String toolName,
            Map<String, Object> remoteDefinition);

    boolean activated(McpExecutionConfig config, String toolName);

    void recordCall(McpRuntimeCallEvent event);

    record McpRuntimeCallEvent(
            String projectId,
            String agentId,
            String nodeId,
            String runId,
            String toolId,
            String mcpId,
            String toolName,
            String status,
            String inputSummary,
            Object output,
            long durationMs,
            Map<String, Object> metadata) {
    }
}
