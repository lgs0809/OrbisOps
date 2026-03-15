package cn.lgs.orbisops.domain.project.model;

import java.util.Locale;

/** Risk classification owned by the Project Workspace context. */
public enum ProjectMcpRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static ProjectMcpRiskLevel failClosed(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) return HIGH;
        try {
            return valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return HIGH;
        }
    }
}
