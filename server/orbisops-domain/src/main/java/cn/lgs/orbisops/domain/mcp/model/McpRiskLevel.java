package cn.lgs.orbisops.domain.mcp.model;

import java.util.Locale;

public enum McpRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
    UNKNOWN;

    public static McpRiskLevel require(String value) {
        String normalized = value == null || value.trim().isBlank()
                ? HIGH.name()
                : value.trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("MCP_RISK_LEVEL_UNKNOWN:" + normalized);
        }
    }

    public static McpRiskLevel failClosed(String value) {
        try {
            return require(value);
        } catch (IllegalArgumentException ignored) {
            return HIGH;
        }
    }

    public boolean lowRisk() {
        return this == LOW;
    }
}
