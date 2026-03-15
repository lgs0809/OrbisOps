package cn.lgs.orbisops.application.project;

import java.util.LinkedHashMap;
import java.util.Map;

public record ProjectResourcePreparation(
        String typeName,
        Map<String, Object> credential,
        Map<String, Object> schema,
        Map<String, Object> permission,
        String status
) {

    public ProjectResourcePreparation {
        typeName = value(typeName);
        credential = copy(credential);
        schema = copy(schema);
        permission = copy(permission);
        status = value(status);
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? Map.of() : new LinkedHashMap<>(source);
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
