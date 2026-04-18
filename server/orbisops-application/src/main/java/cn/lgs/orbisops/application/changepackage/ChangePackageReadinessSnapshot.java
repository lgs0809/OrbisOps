package cn.lgs.orbisops.application.changepackage;

import java.util.LinkedHashMap;
import java.util.Map;

/** Typed operational readiness fact for the ChangePackage store. */
public record ChangePackageReadinessSnapshot(
        String store,
        boolean up,
        boolean autoInit,
        String reason
) {

    public ChangePackageReadinessSnapshot {
        store = required(store, "CHANGE_PACKAGE_READINESS_STORE_REQUIRED");
        reason = reason == null ? "" : reason.trim();
        if (up && !reason.isBlank()) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_READINESS_UP_REASON_CONFLICT");
        }
        if (!up && reason.isBlank()) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_READINESS_DOWN_REASON_REQUIRED");
        }
    }

    public static ChangePackageReadinessSnapshot up(String store, boolean autoInit) {
        return new ChangePackageReadinessSnapshot(store, true, autoInit, "");
    }

    public static ChangePackageReadinessSnapshot down(
            String store,
            boolean autoInit,
            String reason) {
        return new ChangePackageReadinessSnapshot(store, false, autoInit, reason);
    }

    public Map<String, Object> details() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("store", store);
        result.put("status", up ? "UP" : "DOWN");
        result.put("autoInit", autoInit);
        if (!reason.isBlank()) result.put("reason", reason);
        return Map.copyOf(result);
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
