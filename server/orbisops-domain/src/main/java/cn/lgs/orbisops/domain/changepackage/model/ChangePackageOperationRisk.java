package cn.lgs.orbisops.domain.changepackage.model;

import java.util.Locale;

public record ChangePackageOperationRisk(String operationId,
                                         String riskLevel,
                                         String effectType,
                                         String effectScope,
                                         String mutability) {

    public ChangePackageOperationRisk {
        operationId = text(operationId);
        riskLevel = normalized(riskLevel, "HIGH");
        effectType = normalized(effectType, "UNKNOWN");
        if ("MUTATE_TEMP_RESOURCE".equals(effectType)) effectType = "MUTATE_EPHEMERAL";
        effectScope = normalized(effectScope, "UNKNOWN");
        mutability = normalized(mutability, "UNKNOWN");
    }

    private static String normalized(String value, String fallback) {
        String normalized = text(value);
        return (normalized.isBlank() ? fallback : normalized).toUpperCase(Locale.ROOT);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
