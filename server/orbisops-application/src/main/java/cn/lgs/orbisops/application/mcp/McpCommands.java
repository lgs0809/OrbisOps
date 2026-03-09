package cn.lgs.orbisops.application.mcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class McpCommands {

    private McpCommands() {
    }

    public record ProjectRequest(String projectId, Map<String, Object> request) {
        public ProjectRequest {
            projectId = required(projectId, "MCP_PROJECT_ID_REQUIRED");
            request = copy(request);
        }
    }

    public record RuntimeActivation(String projectId,
                                    String runId,
                                    String actor,
                                    Map<String, Object> request,
                                    Map<String, Object> authoritativeRemoteDefinition) {
        public RuntimeActivation {
            projectId = required(projectId, "MCP_PROJECT_ID_REQUIRED");
            runId = required(runId, "MCP_RUN_ID_REQUIRED");
            actor = required(actor, "MCP_ACTOR_REQUIRED");
            request = copy(request);
            authoritativeRemoteDefinition = copy(authoritativeRemoteDefinition);
        }

        public RuntimeActivation(String projectId,
                                 String runId,
                                 String actor,
                                 Map<String, Object> request) {
            this(projectId, runId, actor, request, Map.of());
        }
    }

    public record SchemaHydration(String projectId,
                                  Map<String, Object> request,
                                  Map<String, Object> authoritativeRemoteDefinition) {
        public SchemaHydration {
            projectId = required(projectId, "MCP_PROJECT_ID_REQUIRED");
            request = copy(request);
            authoritativeRemoteDefinition = copy(authoritativeRemoteDefinition);
        }
    }

    public record RuntimeActivationCheck(String projectId,
                                         String runId,
                                         String mcpId,
                                         String toolName) {
        public RuntimeActivationCheck {
            projectId = required(projectId, "MCP_PROJECT_ID_REQUIRED");
            runId = required(runId, "MCP_RUN_ID_REQUIRED");
            mcpId = required(mcpId, "MCP_ID_REQUIRED");
            toolName = required(toolName, "MCP_TOOL_NAME_REQUIRED");
        }
    }

    public record RuntimeCall(String projectId,
                              String agentId,
                              String nodeId,
                              String runId,
                              String toolId,
                              String mcpId,
                              String toolName,
                              String status,
                              Object input,
                              Object output,
                              Long durationMs,
                              Map<String, Object> metadata) {
        public RuntimeCall {
            projectId = required(projectId, "MCP_PROJECT_ID_REQUIRED");
            agentId = text(agentId);
            nodeId = text(nodeId);
            runId = text(runId);
            toolId = required(toolId, "MCP_TOOL_ID_REQUIRED");
            mcpId = text(mcpId);
            toolName = text(toolName);
            status = required(status, "MCP_CALL_STATUS_REQUIRED");
            durationMs = durationMs == null ? 0L : Math.max(0L, durationMs);
            metadata = copy(metadata);
        }
    }

    public record RoutingWarning(String projectId,
                                 String agentId,
                                 String nodeId,
                                 String runId,
                                 String resourceId,
                                 String status,
                                 Map<String, Object> payload) {
        public RoutingWarning {
            projectId = required(projectId, "MCP_PROJECT_ID_REQUIRED");
            agentId = text(agentId);
            nodeId = text(nodeId);
            runId = text(runId);
            resourceId = required(resourceId, "MCP_RESOURCE_ID_REQUIRED");
            status = required(status, "MCP_ROUTING_STATUS_REQUIRED");
            payload = copy(payload);
        }
    }

    public record PolicyMutation(String projectId,
                                 String policyId,
                                 String actor,
                                 McpToolPolicyPatch patch) {
        public PolicyMutation {
            projectId = required(projectId, "MCP_PROJECT_ID_REQUIRED");
            policyId = text(policyId);
            actor = required(actor, "MCP_ACTOR_REQUIRED");
            patch = patch == null ? McpToolPolicyPatch.empty() : patch;
        }
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<String, Object> copy(Map<String, Object> value) {
        if (value == null || value.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
