package cn.lgs.orbisops.domain.alert.service;

import cn.lgs.orbisops.domain.alert.model.AlertEventSnapshot;
import cn.lgs.orbisops.domain.alert.model.AlertRunOutcome;

import java.util.Set;

public final class AlertEventPolicy {

    private static final Set<String> TERMINAL_RUN_STATUSES = Set.of("SUCCEEDED", "FAILED", "CANCELED");

    public int limit(int limit) {
        return Math.max(1, Math.min(limit, 200));
    }

    public boolean terminal(String runStatus) {
        return TERMINAL_RUN_STATUSES.contains(text(runStatus).toUpperCase());
    }

    public boolean supports(AlertRunOutcome outcome) {
        return outcome != null
                && "ALERTMANAGER".equalsIgnoreCase(text(outcome.sourceType()))
                && !text(outcome.triggerEventId()).isBlank();
    }

    public boolean incidentEligible(AlertEventSnapshot event) {
        if (event == null) return false;
        String status = text(event.status()).toUpperCase();
        return "TRIGGERED".equals(status)
                || "QUEUED".equals(status)
                || "FAILED".equals(status)
                || "DEDUPED".equals(status)
                || status.startsWith("RECOVERY_");
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
