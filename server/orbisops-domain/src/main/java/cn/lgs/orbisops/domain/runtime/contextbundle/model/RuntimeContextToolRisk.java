package cn.lgs.orbisops.domain.runtime.contextbundle.model;

import java.util.Locale;

/** Canonical risk vocabulary published into immutable runtime context bundles. */
public enum RuntimeContextToolRisk {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
    UNKNOWN;

    public static RuntimeContextToolRisk from(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return UNKNOWN;
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
