package cn.lgs.orbisops.domain.modelpolicy.model;

import java.util.Locale;

public enum ModelPolicyStatus {
    ENABLED,
    DISABLED;

    public static ModelPolicyStatus require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return ENABLED;
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("MODEL_POLICY_STATUS_UNKNOWN:" + normalized);
        }
    }
}
