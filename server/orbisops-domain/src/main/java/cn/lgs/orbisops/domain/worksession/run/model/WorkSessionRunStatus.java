package cn.lgs.orbisops.domain.worksession.run.model;

import java.util.Locale;

public enum WorkSessionRunStatus {
    PENDING,
    RUNNING,
    WAITING_APPROVAL,
    RECOVERABLE,
    RECOVERY_REVIEW_REQUIRED,
    SUCCEEDED,
    FAILED,
    CANCELED;

    public static WorkSessionRunStatus parse(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("非法 Work Session 状态：" + value, error);
        }
    }

    public static WorkSessionRunStatus terminal(String value) {
        WorkSessionRunStatus status = parse(value);
        if (status != SUCCEEDED && status != FAILED && status != CANCELED) {
            throw new IllegalArgumentException("非法 Work Session 终态：" + value);
        }
        return status;
    }

    public boolean cancellable() {
        return this == PENDING || this == RUNNING || this == WAITING_APPROVAL || this == RECOVERABLE;
    }
}
