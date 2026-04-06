package cn.lgs.orbisops.domain.execution.model;

import java.util.Locale;

public enum ExecutionRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static ExecutionRiskLevel require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return HIGH;
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("EXECUTION_RISK_LEVEL_UNKNOWN:" + normalized);
        }
    }
}
