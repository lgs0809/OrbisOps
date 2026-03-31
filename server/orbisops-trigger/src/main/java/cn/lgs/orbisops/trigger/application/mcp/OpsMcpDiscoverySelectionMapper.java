package cn.lgs.orbisops.trigger.application.mcp;

import cn.lgs.orbisops.application.mcp.McpDiscoverySelectionRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public final class OpsMcpDiscoverySelectionMapper {

    public McpDiscoverySelectionRequest map(String projectId, Map<String, Object> request) {
        Map<String, Object> safe = request == null ? Map.of() : new LinkedHashMap<>(request);
        return new McpDiscoverySelectionRequest(
                projectId,
                firstText(safe, "capability", "capabilityType", "intent"),
                firstText(safe, "toolId", "mcpId"),
                integer(safe.get("limit"), 3),
                text(safe.get("agentId")),
                text(safe.get("nodeId")),
                text(safe.get("runId")),
                text(safe.get("userId")),
                firstText(safe, "userRequest", "query", "request"),
                bool(safe.get("preferReadOnly"), false),
                text(safe.get("stage")),
                safe);
    }

    private String firstText(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            String value = text(source.get(key));
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(text(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean bool(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String normalized = text(value);
        return normalized.isBlank() ? fallback
                : "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
