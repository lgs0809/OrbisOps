package cn.lgs.orbisops.domain.project.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record ProjectResourceDefinition(
        String resourceId,
        String projectId,
        ProjectResourceType type,
        String typeName,
        String name,
        String environment,
        String endpoint,
        Map<String, Object> credential,
        String status,
        Map<String, Object> schema,
        Map<String, Object> permission,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public ProjectResourceDefinition {
        resourceId = required(resourceId, "PROJECT_RESOURCE_ID_REQUIRED");
        projectId = required(projectId, "PROJECT_ID_REQUIRED");
        type = type == null ? ProjectResourceType.from("mysql") : type;
        typeName = text(typeName, type.value());
        name = text(name, type.value());
        environment = text(environment, "prod").toLowerCase(Locale.ROOT);
        endpoint = text(endpoint, type.defaultEndpoint());
        credential = copy(credential);
        status = text(status, "PREVIEW").toUpperCase(Locale.ROOT);
        schema = copy(schema);
        permission = copy(permission);
    }

    public ProjectResourceDefinition update(String name,
                                            String environment,
                                            String endpoint,
                                            Map<String, Object> credential,
                                            String status,
                                            Map<String, Object> schema,
                                            Map<String, Object> permission,
                                            LocalDateTime updatedAt) {
        return new ProjectResourceDefinition(
                resourceId,
                projectId,
                type,
                typeName,
                name,
                environment,
                endpoint,
                credential,
                status,
                schema,
                permission,
                createdAt,
                updatedAt);
    }

    public ProjectResourceDefinition updatePermission(Map<String, Object> permission,
                                                      LocalDateTime updatedAt) {
        return update(name, environment, endpoint, credential, status, schema,
                permission, updatedAt);
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null || source.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String required(String input, String error) {
        String normalized = input == null ? "" : input.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    private static String text(String input, String fallback) {
        String normalized = input == null ? "" : input.trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
