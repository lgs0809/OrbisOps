package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpRiskLevel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record McpPolicySuggestion(
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
        Map<String, Object> argumentPolicy,
        String reason,
        List<String> openQuestions,
        Map<String, Object> rawSuggestion) {

    public McpPolicySuggestion {
        effectType = upper(effectType, "UNKNOWN");
        effectScope = upper(effectScope, "UNKNOWN");
        mutability = upper(mutability, "UNKNOWN");
        capability = upper(capability, readOnly ? "READ_ONLY" : "MUTATING");
        allowedActions = allowedActions == null || allowedActions.isEmpty()
                ? List.of("UNKNOWN_MUTATING")
                : List.copyOf(allowedActions);
        riskLevel = riskLevel == null ? McpRiskLevel.HIGH : riskLevel;
        argumentPolicy = immutable(argumentPolicy);
        reason = text(reason);
        openQuestions = openQuestions == null ? List.of() : List.copyOf(openQuestions);
        rawSuggestion = immutable(rawSuggestion);
    }

    private static Map<String, Object> immutable(Map<String, Object> value) {
        return value == null || value.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    private static String upper(String value, String fallback) {
        String normalized = text(value);
        return (normalized.isBlank() ? fallback : normalized).toUpperCase(java.util.Locale.ROOT);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
