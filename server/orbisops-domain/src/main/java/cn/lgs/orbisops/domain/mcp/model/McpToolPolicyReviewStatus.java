package cn.lgs.orbisops.domain.mcp.model;

import java.util.Locale;

public enum McpToolPolicyReviewStatus {
    UNREVIEWED,
    AI_SUGGESTED,
    SYSTEM_SUGGESTED,
    SYSTEM_VERIFIED,
    HUMAN_REVIEWED,
    REJECTED,
    DISABLED;

    public static McpToolPolicyReviewStatus require(String value) {
        String normalized = value == null || value.trim().isBlank()
                ? UNREVIEWED.name()
                : value.trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("MCP_TOOL_POLICY_REVIEW_STATUS_UNKNOWN:" + normalized);
        }
    }
}
