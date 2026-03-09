package cn.lgs.orbisops.domain.toolexecution.model;

import cn.lgs.orbisops.domain.toolset.model.BoundToolReference;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Provider-neutral execution outcome. Evidence remains represented by the recorded result identity. */
public record ToolExecutionResult(
        boolean success,
        boolean allowed,
        BoundToolReference tool,
        String status,
        Map<String, Object> payload,
        ToolErrorCategory errorCategory,
        String errorCode,
        String message,
        ToolExecutionRecordedResult recorded
) {

    public ToolExecutionResult {
        if (tool == null) throw new IllegalArgumentException("TOOL_EXECUTION_BOUND_REFERENCE_REQUIRED");
        status = required(status, "TOOL_EXECUTION_RESULT_STATUS_REQUIRED");
        payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        errorCategory = errorCategory == null ? ToolErrorCategory.INTERNAL : errorCategory;
        errorCode = text(errorCode);
        message = text(message);
        if (recorded == null) throw new IllegalArgumentException("TOOL_EXECUTION_RECORDED_RESULT_REQUIRED");
        if (success && (!allowed || errorCategory != ToolErrorCategory.NONE)) {
            throw new IllegalArgumentException("TOOL_EXECUTION_SUCCESS_CONFLICT");
        }
        if (!allowed && errorCategory == ToolErrorCategory.NONE) {
            throw new IllegalArgumentException("TOOL_EXECUTION_BLOCKED_CATEGORY_REQUIRED");
        }
    }

    public static ToolExecutionResult allowed(
            BoundToolReference tool,
            Map<String, Object> payload,
            ToolExecutionRecordedResult recorded) {
        return new ToolExecutionResult(
                true, true, tool, "SUCCEEDED", payload,
                ToolErrorCategory.NONE, "", "", recorded);
    }

    public static ToolExecutionResult blocked(
            BoundToolReference tool,
            String errorCode,
            String message,
            Map<String, Object> payload,
            ToolExecutionRecordedResult recorded) {
        return new ToolExecutionResult(
                false, false, tool, "BLOCKED", payload,
                ToolErrorCategory.POLICY_BLOCKED, errorCode, message, recorded);
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
