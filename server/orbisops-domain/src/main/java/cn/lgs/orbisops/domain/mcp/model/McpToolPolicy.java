package cn.lgs.orbisops.domain.mcp.model;

import java.time.LocalDateTime;
import java.util.Locale;

public record McpToolPolicy(long id,
                            String policyId,
                            String projectId,
                            String mcpId,
                            String toolId,
                            String toolName,
                            String schemaHash,
                            String effectType,
                            String effectScope,
                            String mutability,
                            String capability,
                            String allowedActionsJson,
                            McpRiskLevel riskLevel,
                            boolean readOnly,
                            boolean investigateAllowed,
                            boolean prepareAllowed,
                            boolean landAllowed,
                            boolean requiresApprovedPackage,
                            boolean requiresHumanApproval,
                            boolean requiresDryRun,
                            boolean requiresRollbackPlan,
                            String argumentPolicyJson,
                            McpToolPolicyStatus status,
                            McpToolPolicyReviewStatus reviewStatus,
                            String reviewedBy,
                            LocalDateTime reviewedAt,
                            String suggestedBy,
                            LocalDateTime suggestedAt,
                            String metadataJson,
                            LocalDateTime createTime,
                            LocalDateTime updateTime) {

    public McpToolPolicy {
        policyId = required(policyId, "MCP_TOOL_POLICY_ID_REQUIRED");
        projectId = required(projectId, "MCP_TOOL_POLICY_PROJECT_REQUIRED");
        mcpId = required(mcpId, "MCP_TOOL_POLICY_MCP_REQUIRED");
        toolId = safe(toolId);
        toolName = required(toolName, "MCP_TOOL_POLICY_TOOL_REQUIRED");
        schemaHash = required(schemaHash, "MCP_TOOL_POLICY_SCHEMA_HASH_REQUIRED");
        effectType = normalized(effectType, "UNKNOWN");
        effectScope = normalized(effectScope, "UNKNOWN");
        mutability = normalized(mutability, "UNKNOWN");
        capability = normalized(capability, "UNKNOWN");
        allowedActionsJson = json(allowedActionsJson, "[]");
        riskLevel = riskLevel == null ? McpRiskLevel.HIGH : riskLevel;
        argumentPolicyJson = json(argumentPolicyJson, "{}");
        status = status == null ? McpToolPolicyStatus.PENDING_REVIEW : status;
        reviewStatus = reviewStatus == null ? McpToolPolicyReviewStatus.UNREVIEWED : reviewStatus;
        reviewedBy = safe(reviewedBy);
        suggestedBy = safe(suggestedBy);
        metadataJson = json(metadataJson, "{}");
    }

    public boolean activeAndHumanReviewed() {
        return status == McpToolPolicyStatus.ACTIVE
                && reviewStatus == McpToolPolicyReviewStatus.HUMAN_REVIEWED;
    }

    private static String required(String value, String reasonCode) {
        String normalized = safe(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String normalized(String value, String fallback) {
        String normalized = safe(value);
        return (normalized.isBlank() ? fallback : normalized).toUpperCase(Locale.ROOT);
    }

    private static String json(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
