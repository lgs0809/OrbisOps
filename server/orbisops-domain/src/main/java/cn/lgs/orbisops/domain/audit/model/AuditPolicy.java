package cn.lgs.orbisops.domain.audit.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record AuditPolicy(String projectId,
                          int retentionDays,
                          boolean maskingEnabled,
                          boolean exportApprovalRequired,
                          boolean highRiskConfirmationRequired,
                          boolean replayEnabled,
                          AuditPolicyStatus status) {

    public AuditPolicy {
        projectId = value(projectId);
        if (retentionDays < 7 || retentionDays > 3650) {
            throw new IllegalArgumentException("AUDIT_RETENTION_DAYS_INVALID:" + retentionDays);
        }
        if (status == null) throw new IllegalArgumentException("AUDIT_POLICY_STATUS_REQUIRED");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("retentionDays", retentionDays);
        result.put("maskingEnabled", maskingEnabled);
        result.put("exportApprovalRequired", exportApprovalRequired);
        result.put("highRiskConfirmationRequired", highRiskConfirmationRequired);
        result.put("replayEnabled", replayEnabled);
        result.put("status", status.name());
        return result;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
