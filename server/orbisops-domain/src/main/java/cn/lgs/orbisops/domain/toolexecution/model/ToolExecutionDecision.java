package cn.lgs.orbisops.domain.toolexecution.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ToolExecutionDecision(
        boolean allowed,
        String decision,
        String reasonCode,
        String message,
        String riskLevel,
        Map<String, Object> details) {

    public ToolExecutionDecision {
        decision = required(decision, "TOOL_EXECUTION_DECISION_REQUIRED");
        reasonCode = value(reasonCode).isBlank() ? decision : value(reasonCode);
        message = value(message);
        riskLevel = value(riskLevel).isBlank() ? "UNKNOWN" : value(riskLevel).toUpperCase();
        details = details == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public static ToolExecutionDecision allowed(String riskLevel, Map<String, Object> details) {
        return new ToolExecutionDecision(true, "ALLOWED", "ALLOWED", "", riskLevel, details);
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
