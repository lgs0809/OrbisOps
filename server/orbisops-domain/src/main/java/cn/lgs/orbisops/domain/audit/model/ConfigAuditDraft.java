package cn.lgs.orbisops.domain.audit.model;

import java.time.LocalDateTime;
import java.util.Locale;

public record ConfigAuditDraft(
        String auditId,
        String projectId,
        String agentId,
        String moduleName,
        String actionName,
        String targetType,
        String targetId,
        String riskLevel,
        String resultStatus,
        String operatorId,
        String operatorName,
        String operatorRole,
        String clientIp,
        String traceId,
        String beforeJson,
        String afterJson,
        LocalDateTime createTime) {

    public ConfigAuditDraft {
        auditId = required(auditId, "AUDIT_ID_REQUIRED");
        projectId = value(projectId);
        agentId = value(agentId);
        moduleName = required(moduleName, "AUDIT_MODULE_REQUIRED");
        actionName = required(actionName, "AUDIT_ACTION_REQUIRED");
        targetType = value(targetType);
        targetId = value(targetId);
        riskLevel = required(riskLevel, "AUDIT_RISK_LEVEL_REQUIRED").toUpperCase(Locale.ROOT);
        resultStatus = required(resultStatus, "AUDIT_RESULT_STATUS_REQUIRED").toUpperCase(Locale.ROOT);
        operatorId = value(operatorId);
        operatorName = value(operatorName);
        operatorRole = value(operatorRole);
        clientIp = value(clientIp);
        traceId = value(traceId);
        if (createTime == null) throw new IllegalArgumentException("AUDIT_CREATE_TIME_REQUIRED");
    }

    private static String required(String input, String error) {
        String normalized = value(input);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
