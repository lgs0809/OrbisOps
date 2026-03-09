package cn.lgs.orbisops.application.runtime.workflow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record WorkflowBoundToolResult(
        String resultId,
        String evidenceId,
        String outputHash,
        boolean allowed,
        Map<String, Object> payload
) {

    public WorkflowBoundToolResult {
        resultId = required(resultId, "WORKFLOW_TOOL_RESULT_ID_REQUIRED");
        evidenceId = required(evidenceId, "WORKFLOW_TOOL_EVIDENCE_ID_REQUIRED");
        outputHash = required(outputHash, "WORKFLOW_TOOL_OUTPUT_HASH_REQUIRED");
        payload = payload == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
