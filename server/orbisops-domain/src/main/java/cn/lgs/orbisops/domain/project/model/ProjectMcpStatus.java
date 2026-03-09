package cn.lgs.orbisops.domain.project.model;

import java.util.Locale;
import java.util.Set;

public enum ProjectMcpStatus {
    ENABLED,
    DISABLED,
    PENDING_REVIEW,
    STALE,
    REJECTED;

    private static final Set<String> SUPPORTED = Set.of(
            "ENABLED", "DISABLED", "PENDING_REVIEW", "STALE", "REJECTED");

    public static ProjectMcpStatus from(String value) {
        String normalized = value == null || value.trim().isBlank()
                ? "ENABLED"
                : value.trim().toUpperCase(Locale.ROOT);
        return valueOf(SUPPORTED.contains(normalized) ? normalized : "PENDING_REVIEW");
    }
}
