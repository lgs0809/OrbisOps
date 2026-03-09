package cn.lgs.orbisops.application.alert;

import cn.lgs.orbisops.domain.alert.model.AlertRuleDefinition;

public record AlertRuleAuditEvent(
        String action,
        String targetId,
        AlertRuleDefinition before,
        AlertRuleDefinition after,
        String actor) {

    public AlertRuleAuditEvent {
        action = required(action, "ALERT_RULE_AUDIT_ACTION_REQUIRED");
        targetId = required(targetId, "ALERT_RULE_AUDIT_TARGET_REQUIRED");
        actor = required(actor, "ALERT_ACTOR_REQUIRED");
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
