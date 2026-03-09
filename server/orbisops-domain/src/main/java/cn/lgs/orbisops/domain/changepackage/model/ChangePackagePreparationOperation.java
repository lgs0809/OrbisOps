package cn.lgs.orbisops.domain.changepackage.model;

import java.util.Locale;
import java.util.Set;

public record ChangePackagePreparationOperation(String operationId,
                                                String toolName,
                                                String mcpId,
                                                String effectType,
                                                String effectScope,
                                                String mutability,
                                                String riskLevel,
                                                String resourceScope,
                                                boolean writesTargetResource) {

    private static final Set<String> VALIDATION_EFFECTS = Set.of(
            "VALIDATE_ONLY", "DRY_RUN", "MUTATE_EPHEMERAL", "MUTATE_TEST_RESOURCE");

    public ChangePackagePreparationOperation {
        operationId = value(operationId);
        toolName = value(toolName);
        mcpId = value(mcpId);
        effectType = normalizeEffectType(effectType);
        effectScope = upper(effectScope, "UNKNOWN");
        mutability = upper(mutability, "UNKNOWN");
        riskLevel = upper(riskLevel, "HIGH");
        resourceScope = value(resourceScope);
    }

    public boolean completeShape() {
        return !operationId.isBlank()
                && !toolName.isBlank()
                && !mcpId.isBlank()
                && !"UNKNOWN".equals(effectType)
                && !"UNKNOWN".equals(effectScope)
                && !"UNKNOWN".equals(mutability)
                && !resourceScope.isBlank();
    }

    public boolean requiresTrustedValidationProof() {
        return VALIDATION_EFFECTS.contains(effectType)
                && !"PRODUCTION".equals(effectScope)
                && !"TARGET_RESOURCE_WRITE".equals(effectScope);
    }

    public ChangePackageOperationRisk toRisk() {
        return new ChangePackageOperationRisk(operationId, riskLevel, effectType, effectScope, mutability);
    }

    public static String normalizeEffectType(String input) {
        String normalized = upper(input, "UNKNOWN");
        return "MUTATE_TEMP_RESOURCE".equals(normalized)
                ? "MUTATE_EPHEMERAL"
                : normalized;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }

    private static String upper(String input, String fallback) {
        String normalized = value(input).toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? fallback : normalized;
    }
}
