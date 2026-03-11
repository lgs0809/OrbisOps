package cn.lgs.orbisops.domain.knowledge.rag.model;

import java.util.Locale;

public enum RagIngestionJobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED;

    public static RagIngestionJobStatus from(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
