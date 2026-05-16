package cn.lgs.orbisops.application.resourcehealth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed health result for one external resource capability. */
public record ResourceHealthCheck(
        String id,
        String name,
        String endpoint,
        boolean healthy,
        String message,
        Map<String, Object> details) {

    public ResourceHealthCheck {
        id = text(id);
        name = text(name);
        endpoint = text(endpoint);
        message = text(message);
        details = details == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public static ResourceHealthCheck unavailable(
            String id,
            String name,
            String endpoint,
            String message) {
        return new ResourceHealthCheck(
                id,
                name,
                endpoint,
                false,
                message,
                Map.of());
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
