package cn.lgs.orbisops.domain.mcp.model;

import java.util.Locale;

public enum McpToolPolicyStatus {
    PENDING_REVIEW,
    ACTIVE,
    STALE,
    MISSING,
    REJECTED,
    DISABLED;

    public static McpToolPolicyStatus require(String value) {
        String normalized = value == null || value.trim().isBlank()
                ? PENDING_REVIEW.name()
                : value.trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("MCP_TOOL_POLICY_STATUS_UNKNOWN:" + normalized);
        }
    }
}
