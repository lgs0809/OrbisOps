package cn.lgs.orbisops.domain.mcp.service;

import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;
import cn.lgs.orbisops.domain.mcp.model.McpSchemaExposureTier;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReview;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicySuggestion;
import cn.lgs.orbisops.domain.mcp.model.McpToolRuntimeAccess;

import java.util.List;
import java.util.Set;

public class McpToolPolicyGovernance {

    private static final Set<String> TARGET_WRITE_EFFECTS = Set.of(
            "MUTATE_TARGET_RESOURCE", "EXECUTE_EXTERNAL_ACTION", "DELETE_TARGET_RESOURCE");
    private static final Set<String> TARGET_WRITE_SCOPES = Set.of("PRODUCTION", "TARGET_RESOURCE_WRITE");
    private static final Set<String> TARGET_WRITE_MUTABILITY = Set.of("PROD_MUTATING", "DESTRUCTIVE");

    public void validateForHumanPublish(McpToolPolicyReview policy) {
        if ("UNKNOWN".equals(policy.effectType())
                || "UNKNOWN".equals(policy.effectScope())
                || "UNKNOWN".equals(policy.mutability())) {
            throw new IllegalArgumentException("MCP_POLICY_CLASSIFICATION_REQUIRED：发布前必须明确效果类型、作用域和可变性");
        }
        if (policy.allowedActions().isEmpty()
                || policy.allowedActions().stream().anyMatch("UNKNOWN_MUTATING"::equalsIgnoreCase)) {
            throw new IllegalArgumentException("MCP_POLICY_ACTIONS_REQUIRED：发布前必须确认允许的动作");
        }
        boolean targetWrite = TARGET_WRITE_EFFECTS.contains(policy.effectType())
                || TARGET_WRITE_SCOPES.contains(policy.effectScope())
                || TARGET_WRITE_MUTABILITY.contains(policy.mutability());
        boolean readOnlyValidation = Set.of("VALIDATE_ONLY", "DRY_RUN").contains(policy.effectType())
                && "READ_ONLY".equals(policy.mutability()) && !targetWrite;
        if (policy.readOnly()
                && !("NO_EFFECT".equals(policy.effectType()) || "READ_EXTERNAL_STATE".equals(policy.effectType())
                || readOnlyValidation)) {
            throw new IllegalArgumentException("MCP_POLICY_READ_ONLY_CONFLICT：只读策略不能声明写入效果");
        }
        if (targetWrite && (!policy.requiresApprovedPackage() || !policy.requiresHumanApproval())) {
            throw new IllegalArgumentException("MCP_POLICY_TARGET_WRITE_REQUIRES_APPROVAL：目标写工具必须绑定 ChangePackage 和人工审批");
        }
        if (policy.exposureTier() == McpSchemaExposureTier.CORE
                && (!policy.readOnly() || !policy.investigateAllowed()
                || policy.requiresApprovedPackage() || policy.riskLevel() != McpRiskLevel.LOW)) {
            throw new IllegalArgumentException("MCP_POLICY_CORE_REQUIRES_LOW_RISK_READ_ONLY：核心工具只能是无需 ChangePackage 的低风险只读工具");
        }
    }

    public McpToolPolicySuggestion fallbackSuggestion(boolean readOnly,
                                                       McpRiskLevel riskLevel,
                                                       List<String> allowedActions,
                                                       boolean metadataComplete) {
        McpRiskLevel risk = riskLevel == null ? McpRiskLevel.HIGH : riskLevel;
        boolean mutating = !readOnly || mutatingActions(allowedActions);
        boolean highRisk = risk == McpRiskLevel.HIGH || risk == McpRiskLevel.CRITICAL;
        return new McpToolPolicySuggestion(
                readOnly ? "READ_EXTERNAL_STATE" : "MUTATE_TARGET_RESOURCE",
                readOnly ? "TARGET_RESOURCE_READ" : "TARGET_RESOURCE_WRITE",
                mutating ? "PROD_MUTATING" : "READ_ONLY",
                mutating ? "MUTATING" : "READ_ONLY",
                allowedActions == null ? List.of() : allowedActions,
                risk, readOnly, readOnly && !highRisk, readOnly,
                readOnly && !highRisk, mutating || highRisk, true,
                mutating, mutating,
                metadataComplete
                        ? McpToolPolicyReviewStatus.SYSTEM_SUGGESTED
                        : McpToolPolicyReviewStatus.AI_SUGGESTED,
                metadataComplete ? "REMOTE_METADATA_SUGGESTION" : "MISSING_METADATA_SUGGESTION");
    }

    public boolean isPreApprovalExecutable(McpToolRuntimeAccess access) {
        boolean lowOrMedium = access.riskLevel() == McpRiskLevel.LOW
                || access.riskLevel() == McpRiskLevel.MEDIUM;
        boolean readEvidence = access.readOnly() && access.investigateAllowed()
                && !access.requiresApprovedPackage()
                && Set.of("NO_EFFECT", "READ_EXTERNAL_STATE").contains(access.effectType())
                && lowOrMedium;
        boolean validation = access.prepareAllowed()
                && Set.of("VALIDATE_ONLY", "DRY_RUN", "MUTATE_EPHEMERAL", "MUTATE_TEST_RESOURCE")
                .contains(access.effectType())
                && !Set.of("PRODUCTION", "TARGET_RESOURCE_WRITE").contains(access.effectScope())
                && !Set.of("PROD_MUTATING", "DESTRUCTIVE").contains(access.mutability());
        return readEvidence || validation;
    }

    private boolean mutatingActions(List<String> actions) {
        if (actions == null || actions.isEmpty()) return true;
        return actions.stream().map(item -> item.toUpperCase(java.util.Locale.ROOT)).anyMatch(action ->
                action.contains("WRITE") || action.contains("UPDATE") || action.contains("DELETE")
                        || action.contains("CREATE") || action.contains("EXECUTE") || action.contains("RESTART")
                        || action.contains("ROLLBACK") || action.contains("PUBLISH") || action.contains("APPLY")
                        || action.contains("DEPLOY") || action.contains("TRIGGER"));
    }
}
