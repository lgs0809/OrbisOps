package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;
import cn.lgs.orbisops.domain.mcp.model.McpSchemaExposureTier;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyReviewStatus;
import cn.lgs.orbisops.domain.mcp.model.McpToolPolicyStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record McpToolPolicyProjection(
        String policyId,
        McpToolPolicyStatus status,
        McpToolPolicyReviewStatus reviewStatus,
        String effectType,
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
        McpSchemaExposureTier exposureTier,
        Map<String, Object> argumentPolicy) {

    public McpToolPolicyProjection {
        policyId = text(policyId);
        status = status == null ? McpToolPolicyStatus.MISSING : status;
        reviewStatus = reviewStatus == null
                ? McpToolPolicyReviewStatus.UNREVIEWED
                : reviewStatus;
        effectType = upper(effectType, "UNKNOWN");
        effectScope = upper(effectScope, "UNKNOWN");
        mutability = upper(mutability, "UNKNOWN");
        capability = upper(capability, readOnly ? "READ_ONLY" : "MUTATING");
        allowedActions = allowedActions == null || allowedActions.isEmpty()
                ? List.of("UNKNOWN_MUTATING")
                : List.copyOf(allowedActions);
        riskLevel = riskLevel == null ? McpRiskLevel.HIGH : riskLevel;
        exposureTier = exposureTier == null ? McpSchemaExposureTier.EXTENSION : exposureTier;
        argumentPolicy = argumentPolicy == null || argumentPolicy.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(argumentPolicy));
    }

    public static McpToolPolicyProjection missing() {
        return new McpToolPolicyProjection(
                "", McpToolPolicyStatus.MISSING,
                McpToolPolicyReviewStatus.UNREVIEWED,
                "UNKNOWN", "UNKNOWN", "UNKNOWN", "MUTATING",
                List.of("UNKNOWN_MUTATING"), McpRiskLevel.HIGH,
                false, false, false, false,
                true, true, true, true,
                McpSchemaExposureTier.EXTENSION, Map.of());
    }

    public Map<String, Object> view() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policyId", policyId);
        result.put("policyStatus", status.name());
        result.put("status", status.name());
        result.put("reviewStatus", reviewStatus.name());
        result.put("effectType", effectType);
        result.put("effectScope", effectScope);
        result.put("mutability", mutability);
        result.put("capability", capability);
        result.put("allowedActions", allowedActions);
        result.put("riskLevel", riskLevel.name());
        result.put("readOnly", readOnly);
        result.put("investigateAllowed", investigateAllowed);
        result.put("prepareAllowed", prepareAllowed);
        result.put("landAllowed", landAllowed);
        result.put("requiresApprovedPackage", requiresApprovedPackage);
        result.put("requiresHumanApproval", requiresHumanApproval);
        result.put("requiresDryRun", requiresDryRun);
        result.put("requiresRollbackPlan", requiresRollbackPlan);
        result.put("disclosureTier", exposureTier.name());
        result.put("argumentPolicy", argumentPolicy);
        return Map.copyOf(result);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String upper(String value, String fallback) {
        String normalized = text(value);
        return (normalized.isBlank() ? fallback : normalized).toUpperCase(java.util.Locale.ROOT);
    }
}
