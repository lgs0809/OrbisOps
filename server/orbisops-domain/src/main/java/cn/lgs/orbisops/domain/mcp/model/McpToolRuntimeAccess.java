package cn.lgs.orbisops.domain.mcp.model;

import java.util.Locale;

public record McpToolRuntimeAccess(String effectType,
                                   String effectScope,
                                   String mutability,
                                   McpRiskLevel riskLevel,
                                   boolean readOnly,
                                   boolean investigateAllowed,
                                   boolean prepareAllowed,
                                   boolean requiresApprovedPackage) {

    public McpToolRuntimeAccess {
        effectType = normalized(effectType, "UNKNOWN");
        effectScope = normalized(effectScope, "UNKNOWN");
        mutability = normalized(mutability, "UNKNOWN");
        riskLevel = riskLevel == null ? McpRiskLevel.HIGH : riskLevel;
    }

    private static String normalized(String value, String fallback) {
        return (value == null || value.isBlank() ? fallback : value.trim()).toUpperCase(Locale.ROOT);
    }
}
