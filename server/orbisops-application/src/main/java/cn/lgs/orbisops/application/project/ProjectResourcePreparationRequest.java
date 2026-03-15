package cn.lgs.orbisops.application.project;

import java.util.LinkedHashMap;
import java.util.Map;

public record ProjectResourcePreparationRequest(
        String type,
        String resourceId,
        String endpoint,
        Map<String, Object> command,
        Map<String, Object> existingCredential,
        Map<String, Object> existingPermission,
        boolean partialUpdate
) {

    public ProjectResourcePreparationRequest {
        type = value(type);
        resourceId = value(resourceId);
        endpoint = value(endpoint);
        command = copy(command);
        existingCredential = copy(existingCredential);
        existingPermission = copy(existingPermission);
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? Map.of() : new LinkedHashMap<>(source);
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
