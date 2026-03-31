package cn.lgs.orbisops.domain.toolexecution.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ToolExecutionResponse(
        boolean allowed,
        String decision,
        ToolExecutionScope scope,
        ToolExecutionTarget target,
        ToolExecutionRecordedResult recorded,
        Map<String, Object> payload) {

    public ToolExecutionResponse {
        decision = required(decision, "TOOL_EXECUTION_DECISION_REQUIRED");
        if (scope == null) throw new IllegalArgumentException("TOOL_EXECUTION_SCOPE_REQUIRED");
        if (target == null) throw new IllegalArgumentException("TOOL_EXECUTION_TARGET_REQUIRED");
        if (recorded == null) throw new IllegalArgumentException("TOOL_EXECUTION_RECORDED_RESULT_REQUIRED");
        payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    public ToolExecutionResult result(String projectId) {
        if (allowed) {
            return ToolExecutionResult.allowed(target.bind(projectId), payload, recorded);
        }
        return ToolExecutionResult.blocked(
                target.bind(projectId),
                decision,
                message(payload),
                payload,
                recorded);
    }

    private static String message(Map<String, Object> payload) {
        Object message = payload.get("message");
        return message == null ? "" : String.valueOf(message);
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
