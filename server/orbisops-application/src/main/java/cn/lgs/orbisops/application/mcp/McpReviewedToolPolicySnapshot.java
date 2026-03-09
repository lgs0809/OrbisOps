package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;

/** Published review facts exposed by MCP Governance without leaking its aggregate. */
public record McpReviewedToolPolicySnapshot(
        String mcpId,
        String toolName,
        String schemaHash,
        boolean activeAndHumanReviewed
) {

    public McpReviewedToolPolicySnapshot {
        mcpId = value(mcpId);
        toolName = value(toolName);
        schemaHash = value(schemaHash);
    }

    public static McpReviewedToolPolicySnapshot from(McpToolPolicy policy) {
        if (policy == null) throw new IllegalArgumentException("MCP_TOOL_POLICY_REQUIRED");
        return new McpReviewedToolPolicySnapshot(
                policy.mcpId(),
                policy.toolName(),
                policy.schemaHash(),
                policy.activeAndHumanReviewed());
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
