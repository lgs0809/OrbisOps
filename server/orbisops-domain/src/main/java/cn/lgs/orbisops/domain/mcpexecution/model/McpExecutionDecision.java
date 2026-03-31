package cn.lgs.orbisops.domain.mcpexecution.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record McpExecutionDecision(
        boolean allowed,
        String decision,
        String reasonCode,
        String message,
        boolean retryable,
        boolean terminal,
        Map<String, Object> details) {

    public McpExecutionDecision {
        decision = required(decision, "MCP_EXECUTION_DECISION_REQUIRED");
        reasonCode = value(reasonCode).isBlank() ? decision : value(reasonCode);
        message = value(message);
        details = details == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public static McpExecutionDecision allowed(Map<String, Object> details) {
        return new McpExecutionDecision(true, "ALLOWED", "ALLOWED", "", false, false, details);
    }

    public static McpExecutionDecision blocked(String code, String message) {
        return new McpExecutionDecision(false, code, code, message, false, true, Map.of());
    }

    private static String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
