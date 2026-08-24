package cn.lgs.orbisops.domain.worksession.run.model;

public record WorkSessionRecoveryCandidate(
        String runId,
        String projectId,
        String attemptId,
        long stateVersion,
        int toolStartedCount,
        int toolCompletedCount,
        int toolResultCount,
        int unsafeIncompleteToolCount,
        boolean cancelRequested) {

    public WorkSessionRecoveryCandidate {
        runId = required(runId, "WORK_SESSION_RUN_ID_REQUIRED");
        projectId = required(projectId, "WORK_SESSION_PROJECT_ID_REQUIRED");
        attemptId = required(attemptId, "WORK_SESSION_ATTEMPT_ID_REQUIRED");
        toolStartedCount = Math.max(0, toolStartedCount);
        toolCompletedCount = Math.max(0, toolCompletedCount);
        toolResultCount = Math.max(0, toolResultCount);
        unsafeIncompleteToolCount = Math.max(0, unsafeIncompleteToolCount);
    }

    public WorkSessionRecoveryCandidate(
            String runId, String projectId, String attemptId, long stateVersion,
            int toolStartedCount, int toolCompletedCount, int toolResultCount,
            int unsafeIncompleteToolCount) {
        this(runId, projectId, attemptId, stateVersion, toolStartedCount,
                toolCompletedCount, toolResultCount, unsafeIncompleteToolCount, false);
    }

    /** Backward-compatible constructor for older callers/tests that only knew aggregate tool facts. */
    public WorkSessionRecoveryCandidate(
            String runId,
            String projectId,
            String attemptId,
            long stateVersion,
            int toolCheckpointCount,
            int toolResultCount) {
        this(
                runId,
                projectId,
                attemptId,
                stateVersion,
                Math.max(0, toolCheckpointCount),
                0,
                Math.max(0, toolResultCount),
                toolCheckpointCount > 0 || toolResultCount > 0 ? 1 : 0);
    }

    public boolean uncertainSideEffect() {
        return unsafeIncompleteToolCount > 0;
    }

    public boolean hasToolExecution() {
        return toolStartedCount > 0 || toolCompletedCount > 0 || toolResultCount > 0;
    }

    public boolean hasIncompleteToolExecution() {
        return toolStartedCount > toolCompletedCount;
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
