package cn.lgs.orbisops.domain.repair.model;

import java.util.Locale;

public enum CodeDeliveryMode {
    LOCAL_BRANCH,
    GITHUB_PR;

    public static CodeDeliveryMode require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return LOCAL_BRANCH;
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("CODE_DELIVERY_MODE_UNKNOWN:" + normalized);
        }
    }
}
