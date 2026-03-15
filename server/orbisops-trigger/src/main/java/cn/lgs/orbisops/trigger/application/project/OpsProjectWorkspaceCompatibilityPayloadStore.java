package cn.lgs.orbisops.trigger.application.project;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class OpsProjectWorkspaceCompatibilityPayloadStore {

    private final Map<String, Map<String, Object>> projects = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> resources = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> mcps = new LinkedHashMap<>();

    public synchronized void clear() {
        projects.clear();
        resources.clear();
        mcps.clear();
    }

    public synchronized void putProject(String projectId, Map<String, Object> payload) {
        projects.put(required(projectId, "PROJECT_ID_REQUIRED"), copy(payload));
    }

    public synchronized void putResource(
            String projectId,
            String resourceId,
            Map<String, Object> payload) {
        resources.put(key(projectId, resourceId), copy(payload));
    }

    public synchronized void putMcp(
            String projectId,
            String mcpId,
            Map<String, Object> payload) {
        mcps.put(key(projectId, mcpId), copy(payload));
    }

    public synchronized Optional<Map<String, Object>> project(String projectId) {
        return optional(projects.get(value(projectId)));
    }

    public synchronized Optional<Map<String, Object>> resource(
            String projectId,
            String resourceId) {
        return optional(resources.get(key(projectId, resourceId)));
    }

    public synchronized Optional<Map<String, Object>> mcp(
            String projectId,
            String mcpId) {
        return optional(mcps.get(key(projectId, mcpId)));
    }

    private Optional<Map<String, Object>> optional(Map<String, Object> payload) {
        return payload == null ? Optional.empty() : Optional.of(copy(payload));
    }

    private Map<String, Object> copy(Map<String, Object> payload) {
        return payload == null || payload.isEmpty()
                ? Map.of()
                : new LinkedHashMap<>(payload);
    }

    private String key(String projectId, String itemId) {
        return required(projectId, "PROJECT_ID_REQUIRED")
                + "::"
                + required(itemId, "PROJECT_WORKSPACE_ITEM_ID_REQUIRED");
    }

    private String required(String input, String error) {
        String normalized = value(input);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
