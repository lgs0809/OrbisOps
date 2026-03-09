package cn.lgs.orbisops.domain.evidence.model;

import java.util.Locale;

public enum TrustedProofStatus {
    PASSED, SUCCEEDED, SUCCESS, FAILED, UNKNOWN;

    public static TrustedProofStatus require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return UNKNOWN;
        try { return valueOf(normalized); }
        catch (IllegalArgumentException e) { return UNKNOWN; }
    }

    public boolean passed() {
        return this == PASSED || this == SUCCEEDED || this == SUCCESS;
    }
}
