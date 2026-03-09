package cn.lgs.orbisops.application.worksession;

import java.util.List;

public interface WorkSessionRecoveryPort {

    List<RecoveryDecision> recoverExpiredLeases(int limit);

    record RecoveryDecision(String runId,
                            String projectId,
                            String attemptId,
                            String status,
                            String reasonCode) {
        public RecoveryDecision {
            runId = required(runId, "WORK_SESSION_RECOVERY_RUN_ID_REQUIRED");
            projectId = required(projectId, "WORK_SESSION_RECOVERY_PROJECT_ID_REQUIRED");
            attemptId = required(attemptId, "WORK_SESSION_RECOVERY_ATTEMPT_ID_REQUIRED");
            status = required(status, "WORK_SESSION_RECOVERY_STATUS_REQUIRED");
            reasonCode = required(reasonCode, "WORK_SESSION_RECOVERY_REASON_REQUIRED");
        }

        private static String required(String value, String reasonCode) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
            return normalized;
        }
    }
}
