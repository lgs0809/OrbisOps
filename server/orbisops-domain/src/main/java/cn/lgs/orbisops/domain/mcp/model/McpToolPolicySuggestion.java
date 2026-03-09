package cn.lgs.orbisops.domain.mcp.model;

import java.util.List;

public record McpToolPolicySuggestion(String effectType,
                                      String effectScope,
                                      String mutability,
                                      String capability,
                                      List<String> allowedActions,
                                      McpRiskLevel riskLevel,
                                      boolean readOnly,
                                      boolean investigateAllowed,
                                      boolean prepareAllowed,
                                      boolean landAllowed,
                                      boolean requiresApprovedPackage,
                                      boolean requiresHumanApproval,
                                      boolean requiresDryRun,
                                      boolean requiresRollbackPlan,
                                      McpToolPolicyReviewStatus reviewStatus,
                                      String source) {

    public McpToolPolicySuggestion {
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
        riskLevel = riskLevel == null ? McpRiskLevel.HIGH : riskLevel;
        reviewStatus = reviewStatus == null
                ? McpToolPolicyReviewStatus.AI_SUGGESTED
                : reviewStatus;
        source = source == null ? "SYSTEM_SUGGESTED" : source.trim();
    }
}
