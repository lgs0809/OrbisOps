package cn.lgs.orbisops.domain.worksession.run.model;

public record WorkSessionRecoveryDecision(
        String runId,
        String projectId,
        String attemptId,
        WorkSessionRunStatus status,
        String reasonCode) {

    public WorkSessionRecoveryDecision {
        runId = required(runId, "WORK_SESSION_RUN_ID_REQUIRED");
        projectId = required(projectId, "WORK_SESSION_PROJECT_ID_REQUIRED");
        attemptId = required(attemptId, "WORK_SESSION_ATTEMPT_ID_REQUIRED");
        if (status != WorkSessionRunStatus.RECOVERABLE
                && status != WorkSessionRunStatus.RECOVERY_REVIEW_REQUIRED
                && status != WorkSessionRunStatus.CANCELED) {
            throw new IllegalArgumentException("WORK_SESSION_RECOVERY_STATUS_INVALID");
        }
        reasonCode = required(reasonCode, "WORK_SESSION_RECOVERY_REASON_REQUIRED");
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
