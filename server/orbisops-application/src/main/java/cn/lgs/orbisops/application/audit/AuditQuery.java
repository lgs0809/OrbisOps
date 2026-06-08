package cn.lgs.orbisops.application.audit;

import cn.lgs.orbisops.domain.audit.model.ConfigAuditCriteria;

import java.util.Locale;
import java.util.Set;

public record AuditQuery(String projectId,
                         String userId,
                         String agentId,
                         String module,
                         String action,
                         String riskLevel,
                         String startTime,
                         String endTime,
                         int limit) {

    private static final Set<String> RISK_LEVELS = Set.of("", "LOW", "MEDIUM", "HIGH", "CRITICAL");

    public AuditQuery {
        projectId = value(projectId);
        userId = value(userId);
        agentId = value(agentId);
        module = value(module);
        action = value(action);
        riskLevel = value(riskLevel).toUpperCase(Locale.ROOT);
        startTime = value(startTime);
        endTime = value(endTime);
        if (!RISK_LEVELS.contains(riskLevel)) {
            throw new IllegalArgumentException("AUDIT_RISK_LEVEL_UNKNOWN:" + riskLevel);
        }
        if (limit < 1 || limit > 5000) throw new IllegalArgumentException("AUDIT_QUERY_LIMIT_INVALID:" + limit);
    }

    public AuditQuery withLimit(int nextLimit) {
        return new AuditQuery(projectId, userId, agentId, module, action, riskLevel,
                startTime, endTime, nextLimit);
    }

    public AuditQuery withStartTime(String nextStartTime) {
        return new AuditQuery(projectId, userId, agentId, module, action, riskLevel,
                nextStartTime, endTime, limit);
    }

    public ConfigAuditCriteria toCriteria() {
        return new ConfigAuditCriteria(projectId, userId, agentId, module, action,
                riskLevel, startTime, endTime, limit);
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
