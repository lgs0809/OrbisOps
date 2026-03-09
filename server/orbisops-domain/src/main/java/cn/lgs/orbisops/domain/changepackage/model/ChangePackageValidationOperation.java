package cn.lgs.orbisops.domain.changepackage.model;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Typed operation facts used by pre-approval validation policy. */
public record ChangePackageValidationOperation(String operationId,
                                               String toolName,
                                               String adapterType,
                                               String mcpId,
                                               String resourceScope,
                                               String targetEnvironment,
                                               String riskLevel,
                                               String effectType,
                                               String effectScope,
                                               String mutability,
                                               Set<String> presentFields,
                                               boolean writesTargetResource,
                                               boolean requiresChangePackage) {

    private static final List<String> REQUIRED_FIELDS = List.of(
            "operationId",
            "toolName",
            "adapterType",
            "arguments",
            "resourceScope",
            "targetEnvironment",
            "riskLevel",
            "effectType",
            "effectScope",
            "mutability",
            "readOnly",
            "writesTargetResource",
            "requiresChangePackage",
            "requiresApproval");

    public ChangePackageValidationOperation {
        operationId = text(operationId);
        toolName = text(toolName);
        adapterType = text(adapterType);
        mcpId = text(mcpId);
        resourceScope = text(resourceScope);
        targetEnvironment = text(targetEnvironment);
        riskLevel = upper(riskLevel, "HIGH");
        effectType = ChangePackagePreparationOperation.normalizeEffectType(effectType);
        effectScope = upper(effectScope, "UNKNOWN");
        mutability = upper(mutability, "UNKNOWN");
        presentFields = presentFields == null ? Set.of() : Set.copyOf(presentFields);
    }

    public List<String> missingRequiredFields() {
        return REQUIRED_FIELDS.stream()
                .filter(field -> !presentFields.contains(field))
                .toList();
    }

    public boolean missingMcpBinding() {
        return mcpId.isBlank();
    }

    public boolean policyUnknown() {
        return "UNKNOWN".equals(effectType)
                || "UNKNOWN".equals(effectScope)
                || "UNKNOWN".equals(mutability);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String upper(String value, String fallback) {
        String normalized = text(value).toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? fallback : normalized;
    }
}
