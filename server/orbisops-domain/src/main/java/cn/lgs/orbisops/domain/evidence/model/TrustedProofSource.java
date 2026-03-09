package cn.lgs.orbisops.domain.evidence.model;

import java.util.Locale;

public enum TrustedProofSource {
    CI_PROVIDER,
    PLATFORM_CI,
    SANDBOX_VERIFIED,
    APPROVED_VALIDATION_SCRIPT,
    CONTROLLED_BASH_EXECUTED,
    REPAIR_WORKSPACE_VERIFIED,
    TOOL_EXECUTED;

    public static TrustedProofSource require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不可信 proof source：" + normalized, e);
        }
    }
}
