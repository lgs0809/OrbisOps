package cn.lgs.orbisops.domain.audit.service;

import cn.lgs.orbisops.domain.audit.model.AuditPolicy;
import cn.lgs.orbisops.domain.audit.model.AuditPolicyStatus;

import java.util.Map;

public final class AuditPolicyFactory {

    public AuditPolicy create(Map<String, Object> request) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        return new AuditPolicy(
                text(safe.get("projectId")),
                boundedInteger(safe.get("retentionDays"), 7, 3650, 180),
                bool(safe.get("maskingEnabled"), true),
                bool(safe.get("exportApprovalRequired"), true),
                bool(safe.get("highRiskConfirmationRequired"), true),
                bool(safe.get("replayEnabled"), true),
                status(safe.get("status")));
    }

    private int boundedInteger(Object value, int min, int max, int fallback) {
        int parsed;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(text(value));
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return Math.max(min, Math.min(max, parsed));
    }

    private AuditPolicyStatus status(Object value) {
        return "DISABLED".equalsIgnoreCase(text(value))
                ? AuditPolicyStatus.DISABLED
                : AuditPolicyStatus.ENABLED;
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        String normalized = text(value);
        if (normalized.isBlank()) return fallback;
        if ("1".equals(normalized) || "true".equalsIgnoreCase(normalized)
                || "YES".equalsIgnoreCase(normalized) || "ENABLED".equalsIgnoreCase(normalized)) return true;
        if ("0".equals(normalized) || "false".equalsIgnoreCase(normalized)
                || "NO".equalsIgnoreCase(normalized) || "DISABLED".equalsIgnoreCase(normalized)) return false;
        return fallback;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
