package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageOperationRisk;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageRiskInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ChangePackageRiskPolicy {

    private static final List<String> RISK_ORDER = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public RiskAssessment assess(ChangePackageRiskInput input) {
        if (input == null) throw new IllegalArgumentException("CHANGE_PACKAGE_RISK_INPUT_REQUIRED");
        String effective = normalizeRisk(input.requestedRiskLevel(), "MEDIUM");
        List<String> reasons = new ArrayList<>();
        for (ChangePackageOperationRisk operation : input.operations()) {
            effective = max(effective, normalizeRisk(operation.riskLevel(), "HIGH"));
            if ("DELETE_TARGET_RESOURCE".equals(operation.effectType())) {
                effective = max(effective, "CRITICAL");
                reasons.add("DELETE_TARGET_RESOURCE:" + operation.operationId());
            } else if ("UNKNOWN".equals(operation.effectType())
                    || "UNKNOWN".equals(operation.effectScope())) {
                effective = max(effective, "HIGH");
                reasons.add("UNKNOWN_EFFECT:" + operation.operationId());
            } else if ("MUTATE_TARGET_RESOURCE".equals(operation.effectType())
                    || "EXECUTE_EXTERNAL_ACTION".equals(operation.effectType())
                    || "PRODUCTION".equals(operation.effectScope())
                    || "TARGET_RESOURCE_WRITE".equals(operation.effectScope())
                    || "PROD_MUTATING".equals(operation.mutability())
                    || "DESTRUCTIVE".equals(operation.mutability())) {
                effective = max(effective, "HIGH");
                reasons.add("TARGET_WRITE:" + operation.operationId());
            }
        }
        for (String file : input.changedFiles()) {
            String lower = file.toLowerCase(Locale.ROOT);
            if (lower.contains("migration") || lower.contains("schema") || lower.endsWith(".sql")
                    || lower.contains("application-prod") || lower.contains("bootstrap-prod")) {
                effective = max(effective, "HIGH");
                reasons.add("SENSITIVE_FILE:" + file);
            }
        }
        return new RiskAssessment(effective, List.copyOf(reasons));
    }

    private String max(String left, String right) {
        return rank(right) > rank(left) ? right : left;
    }

    private String normalizeRisk(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return RISK_ORDER.contains(normalized) ? normalized : fallback;
    }

    private int rank(String risk) {
        return RISK_ORDER.indexOf(normalizeRisk(risk, "MEDIUM"));
    }

    public record RiskAssessment(String effectiveRiskLevel, List<String> reasons) {
    }
}
