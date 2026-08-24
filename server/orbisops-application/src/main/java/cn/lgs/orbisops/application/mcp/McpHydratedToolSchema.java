package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record McpHydratedToolSchema(
        String projectId,
        String mcpId,
        String toolId,
        String toolName,
        String remoteToolName,
        String resourceType,
        String transportType,
        int requestTimeout,
        List<String> allowedActions,
        McpRiskLevel riskLevel,
        boolean readOnly,
        boolean metadataComplete,
        Map<String, Object> permissionPolicy,
        boolean schemaHydrated,
        Object inputSchema,
        String description,
        String schemaSource,
        String schemaHash,
        McpToolPolicyProjection policy,
        Object outputSchema) {

    public McpHydratedToolSchema(String projectId, String mcpId, String toolId, String toolName,
            String remoteToolName, String resourceType, String transportType, int requestTimeout,
            List<String> allowedActions, McpRiskLevel riskLevel, boolean readOnly, boolean metadataComplete,
            Map<String, Object> permissionPolicy, boolean schemaHydrated, Object inputSchema,
            String description, String schemaSource, String schemaHash, McpToolPolicyProjection policy) {
        this(projectId, mcpId, toolId, toolName, remoteToolName, resourceType, transportType, requestTimeout,
                allowedActions, riskLevel, readOnly, metadataComplete, permissionPolicy, schemaHydrated,
                inputSchema, description, schemaSource, schemaHash, policy, null);
    }

    public McpHydratedToolSchema {
        projectId = required(projectId, "MCP_PROJECT_ID_REQUIRED");
        mcpId = required(mcpId, "MCP_ID_REQUIRED");
        toolId = required(toolId, "MCP_TOOL_ID_REQUIRED");
        toolName = required(toolName, "MCP_TOOL_NAME_REQUIRED");
        remoteToolName = text(remoteToolName);
        resourceType = text(resourceType, "custom");
        transportType = text(transportType, "stdio");
        requestTimeout = requestTimeout <= 0 ? 30 : requestTimeout;
        allowedActions = allowedActions == null || allowedActions.isEmpty()
                ? List.of("UNKNOWN_MUTATING")
                : List.copyOf(allowedActions);
        riskLevel = riskLevel == null ? McpRiskLevel.HIGH : riskLevel;
        permissionPolicy = permissionPolicy == null || permissionPolicy.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(permissionPolicy));
        description = text(description);
        schemaSource = text(schemaSource);
        schemaHash = required(schemaHash, "MCP_TOOL_POLICY_SCHEMA_HASH_REQUIRED");
        policy = policy == null ? McpToolPolicyProjection.missing() : policy;
    }

    public String effectiveToolName() {
        return remoteToolName.isBlank() ? toolName : remoteToolName;
    }

    public McpHydratedToolSchema withPolicy(McpToolPolicyProjection projection) {
        return new McpHydratedToolSchema(
                projectId, mcpId, toolId, toolName, remoteToolName,
                resourceType, transportType, requestTimeout, allowedActions,
                riskLevel, readOnly, metadataComplete, permissionPolicy,
                schemaHydrated, inputSchema, description, schemaSource, schemaHash,
                projection, outputSchema);
    }

    public Map<String, Object> view() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("mcpId", mcpId);
        result.put("toolId", toolId);
        result.put("toolName", toolName);
        result.put("remoteToolName", remoteToolName);
        result.put("resourceType", resourceType);
        result.put("allowedActions", policy.allowedActions());
        result.put("riskLevel", policy.riskLevel().name());
        result.put("readOnly", policy.readOnly());
        result.put("metadataComplete", metadataComplete);
        result.put("permissionPolicy", permissionPolicy);
        result.put("transportType", transportType);
        result.put("requestTimeout", requestTimeout);
        result.put("schemaHydrated", schemaHydrated);
        if (schemaHydrated) {
            result.put("schema", inputSchema);
            result.put("description", description);
            result.put("schemaSource", schemaSource);
            if (outputSchema != null) result.put("outputSchema", outputSchema);
        }
        result.put("schemaHash", schemaHash);
        result.putAll(policy.view());
        return Collections.unmodifiableMap(result);
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String text(String value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }
}
