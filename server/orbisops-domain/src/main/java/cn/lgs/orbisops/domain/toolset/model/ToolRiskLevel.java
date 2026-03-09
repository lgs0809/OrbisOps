package cn.lgs.orbisops.domain.toolset.model;

import java.util.Locale;

public enum ToolRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static ToolRiskLevel require(String value, ToolRiskLevel fallback) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return fallback;
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("TOOL_RISK_LEVEL_UNKNOWN:" + normalized);
        }
    }
}
