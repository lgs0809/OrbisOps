package cn.lgs.orbisops.domain.audit.model;

import java.time.LocalDateTime;

public record ConfigAuditEntry(
        Long id,
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

    public ConfigAuditEntry {
        auditId = value(auditId);
        projectId = value(projectId);
        agentId = value(agentId);
        moduleName = value(moduleName);
        actionName = value(actionName);
        targetType = value(targetType);
        targetId = value(targetId);
        riskLevel = value(riskLevel);
        resultStatus = value(resultStatus);
        operatorId = value(operatorId);
        operatorName = value(operatorName);
        operatorRole = value(operatorRole);
        clientIp = value(clientIp);
        traceId = value(traceId);
    }

    public static ConfigAuditEntry from(ConfigAuditDraft draft) {
        return new ConfigAuditEntry(
                null, draft.auditId(), draft.projectId(), draft.agentId(), draft.moduleName(),
                draft.actionName(), draft.targetType(), draft.targetId(), draft.riskLevel(),
                draft.resultStatus(), draft.operatorId(), draft.operatorName(), draft.operatorRole(),
                draft.clientIp(), draft.traceId(), draft.beforeJson(), draft.afterJson(), draft.createTime());
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
