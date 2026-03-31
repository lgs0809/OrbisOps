package cn.lgs.orbisops.application.mcp;

import java.util.List;
import java.util.Map;

public interface McpRuntimeOperationsPort {

    McpRuntimeActivationResult activateRuntimeTool(McpRuntimeActivationRequest command);

    List<Map<String, Object>> runtimeCatalog(String projectId,
                                             String toolIdOrMcpId,
                                             List<String> allowedTools,
                                             List<String> blockedTools);

    List<Map<String, Object>> runtimeCatalog(String projectId,
                                             String toolIdOrMcpId,
                                             List<String> allowedTools,
                                             List<String> blockedTools,
                                             String executionStage);

    List<Map<String, Object>> runtimeExecutableTools(String projectId,
                                                     String toolIdOrMcpId,
                                                     List<String> allowedTools,
                                                     List<String> blockedTools);

    List<Map<String, Object>> runtimeExecutableTools(String projectId,
                                                     String toolIdOrMcpId,
                                                     List<String> allowedTools,
                                                     List<String> blockedTools,
                                                     String executionStage);

    boolean isRuntimeToolActivated(McpCommands.RuntimeActivationCheck command);

    boolean requiresRuntimeActivation(McpHydratedToolSchema schema);

    void recordMcpCall(McpCommands.RuntimeCall command);

    void recordToolRoutingWarning(McpCommands.RoutingWarning command);
}
