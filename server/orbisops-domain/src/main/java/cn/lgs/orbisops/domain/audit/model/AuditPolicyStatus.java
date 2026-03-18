package cn.lgs.orbisops.domain.audit.model;

import java.util.Locale;

public enum AuditPolicyStatus {
    ENABLED,
    DISABLED;

    public static AuditPolicyStatus require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return ENABLED;
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("AUDIT_POLICY_STATUS_UNKNOWN:" + normalized);
        }
    }
}
