package cn.lgs.orbisops.application.project;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ProjectWorkspaceRuntimeResource(
        String resourceId,
        String projectId,
        String type,
        String environment,
        String endpoint,
        Map<String, Object> credential,
        Map<String, Object> permission,
        String status
) {

    public ProjectWorkspaceRuntimeResource {
        resourceId = text(resourceId);
        projectId = text(projectId);
        type = text(type);
        environment = text(environment);
        endpoint = text(endpoint);
        credential = copy(credential);
        permission = copy(permission);
        status = text(status);
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null || source.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
