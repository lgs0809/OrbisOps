package cn.lgs.orbisops.domain.mcp.model;

import java.util.Locale;

public enum McpSchemaExposureTier {
    CORE,
    EXTENSION;

    public static McpSchemaExposureTier require(String value) {
        String normalized = value == null || value.trim().isBlank()
                ? EXTENSION.name()
                : value.trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("MCP_SCHEMA_EXPOSURE_TIER_UNKNOWN:" + normalized);
        }
    }
}
