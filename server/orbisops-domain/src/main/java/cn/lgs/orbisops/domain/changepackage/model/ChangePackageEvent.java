package cn.lgs.orbisops.domain.changepackage.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ChangePackageEvent(long id,
                                 String eventId,
                                 String packageId,
                                 String eventType,
                                 String actor,
                                 String summary,
                                 Map<String, Object> payload,
                                 LocalDateTime createdAt) {

    public ChangePackageEvent {
        eventId = required(eventId, "CHANGE_PACKAGE_EVENT_ID_REQUIRED");
        packageId = required(packageId, "CHANGE_PACKAGE_EVENT_PACKAGE_ID_REQUIRED");
        eventType = required(eventType, "CHANGE_PACKAGE_EVENT_TYPE_REQUIRED");
        actor = text(actor);
        summary = text(summary);
        payload = payload == null || payload.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
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
