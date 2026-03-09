package cn.lgs.orbisops.application.worksession;

/** Executes a durable recovery decision without exposing runtime request types to Application. */
public interface WorkSessionRecoveryExecutionPort {

    RecoveryExecutionOutcome execute(WorkSessionRecoveryPort.RecoveryDecision decision);

    record RecoveryExecutionOutcome(
            String runId,
            String projectId,
            String attemptId,
            String status,
            String reasonCode) {

        public RecoveryExecutionOutcome {
            runId = required(runId, "WORK_SESSION_RECOVERY_EXECUTION_RUN_ID_REQUIRED");
            projectId = required(projectId, "WORK_SESSION_RECOVERY_EXECUTION_PROJECT_ID_REQUIRED");
            attemptId = required(attemptId, "WORK_SESSION_RECOVERY_EXECUTION_ATTEMPT_ID_REQUIRED");
            status = required(status, "WORK_SESSION_RECOVERY_EXECUTION_STATUS_REQUIRED");
            reasonCode = required(reasonCode, "WORK_SESSION_RECOVERY_EXECUTION_REASON_REQUIRED");
        }

        public static RecoveryExecutionOutcome scheduled(
                WorkSessionRecoveryPort.RecoveryDecision decision) {
            return new RecoveryExecutionOutcome(
                    decision.runId(), decision.projectId(), decision.attemptId(),
                    "SCHEDULED", "WORK_SESSION_RECOVERY_SCHEDULED");
        }

        public static RecoveryExecutionOutcome skipped(
                WorkSessionRecoveryPort.RecoveryDecision decision) {
            return new RecoveryExecutionOutcome(
                    decision.runId(), decision.projectId(), decision.attemptId(),
                    "SKIPPED", "WORK_SESSION_RECOVERY_NOT_EXECUTABLE");
        }

        public static RecoveryExecutionOutcome failed(
                WorkSessionRecoveryPort.RecoveryDecision decision,
                String reasonCode) {
            return new RecoveryExecutionOutcome(
                    decision.runId(), decision.projectId(), decision.attemptId(),
                    "FAILED", required(reasonCode, "WORK_SESSION_RECOVERY_EXECUTION_REASON_REQUIRED"));
        }

        private static String required(String value, String reasonCode) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
            return normalized;
        }
    }
}
