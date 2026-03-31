package cn.lgs.orbisops.domain.toolexecution.service;

import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionDecision;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolExecutionPolicy {

    /** A caller may narrow an invocation to reads; this never grants otherwise denied authority. */
    public ToolExecutionDecision restrictToReadOnly(ToolExecutionRequest request,
                                                   ToolExecutionTarget target,
                                                   ToolExecutionDecision decision) {
        if (decision.allowed() && Boolean.TRUE.equals(request.requestContext().get("requireReadOnly"))
                && !target.readOnly()) {
            return new ToolExecutionDecision(false, "BLOCKED", "READ_ONLY_REQUIRED",
                    "This verification invocation permits read-only tools only", target.riskLevel(), Map.of());
        }
        return decision;
    }

    public String source(ToolExecutionRequest request) {
        return request.scope().name() + ":" + request.toolsetId() + ":" + request.toolName();
    }

    public String evidenceSourceType(String source) {
        String normalized = value(source).isBlank() ? "TOOL" : value(source).toUpperCase();
        if (normalized.contains("MCP")) return "MCP";
        if (normalized.contains("DRY_RUN")) return "DRY_RUN";
        if (normalized.contains("SANDBOX")) return "SANDBOX";
        if (normalized.contains("PREFLIGHT") || normalized.contains("VALIDATION")) return "PREFLIGHT";
        if (normalized.contains("REPAIR")) return "REPAIR_DIFF";
        if (normalized.contains("BASH") || normalized.contains("TEST")) return "TEST";
        if (normalized.contains("RAG")) return "RAG";
        return "TOOL";
    }

    public String outputStatus(Object output, String source) {
        if ("TOOL_BLOCKED".equals(source)) return "BLOCKED";
        if (output instanceof Map<?, ?> map) {
            String status = value(map.get("status"));
            if (!status.isBlank()) return status.toUpperCase();
        }
        return "SUCCEEDED";
    }

    public Map<String, Object> blockedPayload(
            ToolExecutionTarget target,
            ToolExecutionDecision decision) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status", "BLOCKED");
        output.put("allowed", false);
        output.put("decision", decision.decision());
        output.put("reasonCode", decision.reasonCode());
        output.put("message", decision.message());
        output.put("toolsetId", target.toolsetId());
        output.put("toolName", target.toolName());
        return output;
    }

    public Map<String, Object> responsePayload(Object output, int rawPreviewLimit) {
        Map<String, Object> response = new LinkedHashMap<>();
        if (output instanceof Map<?, ?> map) {
            map.forEach((key, value) -> response.put(String.valueOf(key), value));
        } else {
            response.put("rawPreview", abbreviate(output == null ? "" : String.valueOf(output), rawPreviewLimit));
        }
        return response;
    }

    public Map<String, Object> startedCheckpoint(
            String toolCallId,
            ToolExecutionTarget target,
            ToolExecutionRequest request,
            String argumentsHash,
            ToolExecutionDecision decision) {
        return Map.of(
                "toolCallId", toolCallId,
                "toolsetId", target.toolsetId(),
                "toolName", target.toolName(),
                "adapterType", target.adapterType(),
                "executionScope", request.scope().name(),
                "argumentsHash", argumentsHash,
                "riskLevel", decision.riskLevel().isBlank() ? target.riskLevel() : decision.riskLevel(),
                "readOnly", target.readOnly(),
                "writesTargetResource", target.writesTargetResource());
    }

    public Map<String, Object> completedCheckpoint(
            String toolCallId,
            ToolExecutionTarget target,
            String resultId,
            String outputHash) {
        return Map.of(
                "toolCallId", toolCallId,
                "resultId", value(resultId),
                "outputHash", value(outputHash),
                "toolsetId", target.toolsetId(),
                "toolName", target.toolName());
    }

    public Map<String, Object> failedCheckpoint(
            String toolCallId,
            ToolExecutionTarget target,
            RuntimeException error) {
        return Map.of(
                "toolCallId", toolCallId,
                "toolsetId", target.toolsetId(),
                "toolName", target.toolName(),
                "errorType", error == null ? "RuntimeException" : error.getClass().getSimpleName());
    }

    public String failureMessage(RuntimeException error) {
        return error == null || error.getMessage() == null ? "" : error.getMessage();
    }

    public boolean verifiedEvidence(String source) {
        return !"TOOL_BLOCKED".equals(source);
    }

    public ToolExecutionScope landingScope() {
        return ToolExecutionScope.APPROVED_LANDING;
    }

    private String abbreviate(String value, int maxChars) {
        String source = value == null ? "" : value;
        return source.length() <= maxChars ? source : source.substring(0, maxChars);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
