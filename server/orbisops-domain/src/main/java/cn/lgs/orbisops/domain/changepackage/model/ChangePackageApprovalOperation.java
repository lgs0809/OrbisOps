package cn.lgs.orbisops.domain.changepackage.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Typed operation facts used by ChangePackage approval safety policy. */
public record ChangePackageApprovalOperation(Map<String, Object> raw,
                                             String operationId,
                                             String mcpId,
                                             String toolName,
                                             String adapterType,
                                             String resourceScope,
                                             String riskLevel,
                                             String targetEnvironment,
                                             String effectType,
                                             String effectScope,
                                             String mutability,
                                             Set<String> presentFields,
                                             boolean writesTargetResource,
                                             boolean requiresChangePackage,
                                             boolean requiresApproval,
                                             boolean preconditionsPresent,
                                             boolean postCheckPresent,
                                             boolean rollbackPlanPresent,
                                             boolean rollbackPreconditionPresent,
                                             boolean manualFallbackPresent,
                                             String argumentsHash,
                                             String preconditionHash,
                                             String postCheckHash,
                                             String rollbackHash,
                                             String operationHash) {

    public ChangePackageApprovalOperation {
        raw = raw == null || raw.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(raw));
        operationId = text(operationId);
        mcpId = text(mcpId);
        toolName = text(toolName);
        adapterType = text(adapterType);
        resourceScope = text(resourceScope);
        riskLevel = upper(riskLevel, "HIGH");
        targetEnvironment = text(targetEnvironment);
        effectType = ChangePackagePreparationOperation.normalizeEffectType(effectType);
        effectScope = upper(effectScope, "UNKNOWN");
        mutability = upper(mutability, "UNKNOWN");
        presentFields = presentFields == null ? Set.of() : Set.copyOf(presentFields);
        argumentsHash = text(argumentsHash);
        preconditionHash = text(preconditionHash);
        postCheckHash = text(postCheckHash);
        rollbackHash = text(rollbackHash);
        operationHash = text(operationHash);
    }

    public boolean present(String field) {
        return presentFields.contains(field);
    }

    public boolean policyUnknown() {
        return "UNKNOWN".equals(effectType)
                || "UNKNOWN".equals(effectScope)
                || "UNKNOWN".equals(mutability);
    }

    private static String upper(String value, String fallback) {
        String normalized = text(value).toUpperCase(java.util.Locale.ROOT);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
