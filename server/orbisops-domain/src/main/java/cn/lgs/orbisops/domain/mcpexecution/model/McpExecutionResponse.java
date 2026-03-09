package cn.lgs.orbisops.domain.mcpexecution.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record McpExecutionResponse(
        boolean allowed,
        String decision,
        String executionScope,
        String toolsetId,
        String toolName,
        McpExecutionRecordedResult recorded,
        Map<String, Object> payload) {

    public McpExecutionResponse {
        decision = required(decision, "MCP_EXECUTION_RESPONSE_DECISION_REQUIRED");
        executionScope = required(executionScope, "MCP_EXECUTION_RESPONSE_SCOPE_REQUIRED");
        toolsetId = required(toolsetId, "MCP_EXECUTION_RESPONSE_TOOLSET_REQUIRED");
        toolName = required(toolName, "MCP_EXECUTION_RESPONSE_TOOL_REQUIRED");
        if (recorded == null) throw new IllegalArgumentException("MCP_EXECUTION_RECORDED_RESULT_REQUIRED");
        payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
