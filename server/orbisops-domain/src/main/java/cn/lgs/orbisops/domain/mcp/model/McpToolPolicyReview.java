package cn.lgs.orbisops.domain.mcp.model;

import java.util.List;
import java.util.Locale;

public record McpToolPolicyReview(String effectType,
                                  String effectScope,
                                  String mutability,
                                  List<String> allowedActions,
                                  boolean readOnly,
                                  boolean investigateAllowed,
                                  boolean requiresApprovedPackage,
                                  boolean requiresHumanApproval,
                                  McpRiskLevel riskLevel,
                                  McpSchemaExposureTier exposureTier) {

    public McpToolPolicyReview {
        effectType = normalized(effectType, "UNKNOWN");
        effectScope = normalized(effectScope, "UNKNOWN");
        mutability = normalized(mutability, "UNKNOWN");
        allowedActions = allowedActions == null ? List.of() : allowedActions.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        riskLevel = riskLevel == null ? McpRiskLevel.HIGH : riskLevel;
        exposureTier = exposureTier == null ? McpSchemaExposureTier.EXTENSION : exposureTier;
    }

    private static String normalized(String value, String fallback) {
        return (value == null || value.isBlank() ? fallback : value.trim()).toUpperCase(Locale.ROOT);
    }
}
