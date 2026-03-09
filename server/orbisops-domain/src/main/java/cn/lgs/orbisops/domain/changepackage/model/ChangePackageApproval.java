package cn.lgs.orbisops.domain.changepackage.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ChangePackageApproval(String approvalId,
                                    String packageId,
                                    String projectId,
                                    int version,
                                    String packageHash,
                                    String riskLevel,
                                    String approver,
                                    String actorScope,
                                    String decision,
                                    boolean adminConfirmation,
                                    Map<String, Object> metadata) {

    public ChangePackageApproval {
        approvalId = required(approvalId, "CHANGE_PACKAGE_APPROVAL_ID_REQUIRED");
        packageId = required(packageId, "CHANGE_PACKAGE_APPROVAL_PACKAGE_ID_REQUIRED");
        projectId = required(projectId, "CHANGE_PACKAGE_APPROVAL_PROJECT_ID_REQUIRED");
        if (version <= 0) throw new IllegalArgumentException("CHANGE_PACKAGE_APPROVAL_VERSION_INVALID");
        packageHash = required(packageHash, "CHANGE_PACKAGE_APPROVAL_HASH_REQUIRED");
        riskLevel = required(riskLevel, "CHANGE_PACKAGE_APPROVAL_RISK_REQUIRED")
                .toUpperCase(java.util.Locale.ROOT);
        approver = required(approver, "CHANGE_PACKAGE_APPROVER_REQUIRED");
        actorScope = text(actorScope);
        decision = required(decision, "CHANGE_PACKAGE_APPROVAL_DECISION_REQUIRED")
                .toUpperCase(java.util.Locale.ROOT);
        metadata = metadata == null || metadata.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
