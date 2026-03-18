package cn.lgs.orbisops.domain.audit.model;

import java.util.Locale;
import java.util.Set;

public record ConfigAuditCriteria(
        String projectId,
        String userId,
        String agentId,
        String moduleName,
        String actionName,
        String riskLevel,
        String startTime,
        String endTime,
        int limit) {

    private static final Set<String> RISK_LEVELS = Set.of("", "LOW", "MEDIUM", "HIGH", "CRITICAL");

    public ConfigAuditCriteria {
        projectId = value(projectId);
        userId = value(userId);
        agentId = value(agentId);
        moduleName = value(moduleName);
        actionName = value(actionName);
        riskLevel = value(riskLevel).toUpperCase(Locale.ROOT);
        startTime = value(startTime);
        endTime = value(endTime);
        if (!RISK_LEVELS.contains(riskLevel)) {
            throw new IllegalArgumentException("AUDIT_RISK_LEVEL_UNKNOWN:" + riskLevel);
        }
        if (limit < 1 || limit > 5000) {
            throw new IllegalArgumentException("AUDIT_QUERY_LIMIT_INVALID:" + limit);
        }
    }

    public ConfigAuditCriteria withLimit(int nextLimit) {
        return new ConfigAuditCriteria(projectId, userId, agentId, moduleName, actionName,
                riskLevel, startTime, endTime, nextLimit);
    }

    public static ConfigAuditCriteria empty() {
        return new ConfigAuditCriteria("", "", "", "", "", "", "", "", 100);
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
