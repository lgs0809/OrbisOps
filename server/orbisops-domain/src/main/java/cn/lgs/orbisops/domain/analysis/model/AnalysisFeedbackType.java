package cn.lgs.orbisops.domain.analysis.model;

import java.util.Locale;

public enum AnalysisFeedbackType {
    HELPFUL,
    INACCURATE,
    INSUFFICIENT_EVIDENCE;

    public static AnalysisFeedbackType require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("ANALYSIS_FEEDBACK_TYPE_REQUIRED");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("ANALYSIS_FEEDBACK_TYPE_UNKNOWN:" + normalized);
        }
    }

    public boolean negative() {
        return this != HELPFUL;
    }

    public boolean evidenceSufficient() {
        return this != INSUFFICIENT_EVIDENCE;
    }
}
