package cn.lgs.orbisops.application.project;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** ACL for stable workspace mutation identifiers while preserving open request payloads. */
public record ProjectWorkspaceMutationCommand(
        Map<String, Object> payload,
        String actor) {

    public ProjectWorkspaceMutationCommand {
        actor = required(actor, "PROJECT_ACTOR_REQUIRED");
        Map<String, Object> copy = new LinkedHashMap<>();
        if (payload != null) copy.putAll(payload);
        copy.put("actor", actor);
        payload = Collections.unmodifiableMap(copy);
    }

    public static ProjectWorkspaceMutationCommand from(Map<String, Object> request, String actor) {
        return new ProjectWorkspaceMutationCommand(request, actor);
    }

    public String projectId() {
        return required(payload.get("projectId"), "PROJECT_ID_REQUIRED");
    }

    public String resourceId() {
        return required(payload.get("resourceId"), "PROJECT_RESOURCE_ID_REQUIRED");
    }

    private static String required(Object value, String reasonCode) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
