package cn.lgs.orbisops.domain.mcp.model;

import java.time.LocalDateTime;

/**
 * Tolerant read model for the admin catalog only. It intentionally does not
 * participate in runtime authorization/execution and may represent legacy rows
 * that violate the executable {@link McpToolPolicy} invariants.
 */
public record McpToolPolicyAdminRecord(
        long id,
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
        String riskLevel,
        boolean readOnly,
        boolean investigateAllowed,
        boolean prepareAllowed,
        boolean landAllowed,
        boolean requiresApprovedPackage,
        boolean requiresHumanApproval,
        boolean requiresDryRun,
        boolean requiresRollbackPlan,
        String argumentPolicyJson,
        String status,
        String reviewStatus,
        String reviewedBy,
        LocalDateTime reviewedAt,
        String suggestedBy,
        LocalDateTime suggestedAt,
        String metadataJson,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public boolean legacyInvalid() {
        return schemaHash == null || schemaHash.trim().isBlank();
    }

    public String invalidReason() {
        return legacyInvalid() ? "MCP_TOOL_POLICY_SCHEMA_HASH_REQUIRED" : "";
    }
}
