package cn.lgs.orbisops.domain.analysis.model;

import java.util.Locale;

public enum AnalysisRunStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED;

    public static AnalysisRunStatus require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("ANALYSIS_RUN_STATUS_REQUIRED");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("ANALYSIS_RUN_STATUS_UNKNOWN:" + normalized);
        }
    }

    public boolean terminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED;
    }
}
