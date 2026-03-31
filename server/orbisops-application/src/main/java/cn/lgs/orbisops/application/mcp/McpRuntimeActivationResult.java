package cn.lgs.orbisops.application.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

public record McpRuntimeActivationResult(
        String activationId,
        String runId,
        String mcpId,
        String toolName,
        String schemaHash,
        Object inputSchema,
        String description,
        Map<String, Object> argumentPolicy) {

    public McpRuntimeActivationResult {
        activationId = required(activationId, "MCP_ACTIVATION_ID_REQUIRED");
        runId = required(runId, "MCP_RUN_ID_REQUIRED");
        mcpId = required(mcpId, "MCP_ID_REQUIRED");
        toolName = required(toolName, "MCP_TOOL_NAME_REQUIRED");
        schemaHash = required(schemaHash, "MCP_ACTIVATION_SCHEMA_HASH_REQUIRED");
        description = description == null ? "" : description.trim();
        argumentPolicy = argumentPolicy == null ? Map.of() : Map.copyOf(argumentPolicy);
    }

    public Map<String, Object> view() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activationId", activationId);
        result.put("runId", runId);
        result.put("mcpId", mcpId);
        result.put("toolName", toolName);
        result.put("schemaHash", schemaHash);
        result.put("schema", inputSchema == null ? Map.of() : inputSchema);
        result.put("description", description);
        result.put("argumentPolicy", argumentPolicy);
        result.put("status", "ACTIVE");
        result.put("message", "工具已在当前 Work Session 激活；下一次推理可按已披露 schema 调用。");
        return Map.copyOf(result);
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
