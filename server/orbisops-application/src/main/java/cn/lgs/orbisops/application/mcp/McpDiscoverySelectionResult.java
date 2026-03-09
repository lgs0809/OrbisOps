package cn.lgs.orbisops.application.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record McpDiscoverySelectionResult(
        String decisionId,
        String projectId,
        String agentId,
        String nodeId,
        String runId,
        String capability,
        List<McpDiscoveryToolCandidate> selectedTools,
        String reason) {

    public McpDiscoverySelectionResult {
        decisionId = required(decisionId, "MCP_DECISION_ID_REQUIRED");
        projectId = required(projectId, "MCP_PROJECT_ID_REQUIRED");
        agentId = text(agentId);
        nodeId = text(nodeId);
        runId = text(runId);
        capability = text(capability);
        selectedTools = selectedTools == null ? List.of() : List.copyOf(selectedTools);
        reason = text(reason);
    }

    public boolean selected() {
        return !selectedTools.isEmpty();
    }

    public String status() {
        return selected() ? "SELECTED" : "SKIPPED";
    }

    public List<Map<String, Object>> selectedToolViews() {
        return selectedTools.stream().map(McpDiscoveryToolCandidate::view).toList();
    }

    public Map<String, Object> view(Map<String, Object> requestPayload) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("decisionId", decisionId);
        result.put("projectId", projectId);
        result.put("agentId", agentId);
        result.put("nodeId", nodeId);
        result.put("runId", runId);
        result.put("capability", capability);
        result.put("request", requestPayload == null ? Map.of() : requestPayload);
        result.put("selectedTools", selectedToolViews());
        result.put("reason", reason);
        result.put("status", status());
        return Map.copyOf(result);
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
