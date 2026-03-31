package cn.lgs.orbisops.application.toolset;

import cn.lgs.orbisops.domain.toolset.model.ToolsetRefreshStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed result of refreshing one MCP-backed Toolset. */
public record ToolsetRefreshOutcome(
        String projectId,
        String toolsetId,
        ToolsetRefreshStatus status,
        String message,
        Map<String, Object> summary
) {

    public ToolsetRefreshOutcome {
        projectId = required(projectId, "TOOLSET_PROJECT_ID_REQUIRED");
        toolsetId = required(toolsetId, "TOOLSET_ID_REQUIRED");
        if (status == null) throw new IllegalArgumentException("TOOLSET_REFRESH_STATUS_REQUIRED");
        message = message == null ? "" : message.trim();
        summary = summary == null || summary.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(summary));
    }

    public Map<String, Object> view() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("toolsetId", toolsetId);
        result.put("status", status.name());
        if (!message.isBlank()) result.put("message", message);
        if (!summary.isEmpty()) result.put("summary", summary);
        return Map.copyOf(result);
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
