package cn.lgs.orbisops.domain.mcp.model;

import java.util.Locale;

public enum McpTemplateStatus {
    ENABLED,
    DISABLED,
    DELETED;

    public static McpTemplateStatus require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("MCP_TEMPLATE_STATUS_REQUIRED");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("MCP_TEMPLATE_STATUS_UNKNOWN:" + normalized);
        }
    }
}
