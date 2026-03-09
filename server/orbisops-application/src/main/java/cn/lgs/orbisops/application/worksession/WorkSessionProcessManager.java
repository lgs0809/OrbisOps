package cn.lgs.orbisops.application.worksession;

import java.util.ArrayList;
import java.util.List;

public final class WorkSessionProcessManager {

    private final WorkSessionRecoveryPort recoveryPort;
    private final WorkSessionRecoveryExecutionPort recoveryExecution;

    public WorkSessionProcessManager(WorkSessionRecoveryPort recoveryPort) {
        this(recoveryPort, decision -> WorkSessionRecoveryExecutionPort.RecoveryExecutionOutcome.skipped(decision));
    }

    public WorkSessionProcessManager(
            WorkSessionRecoveryPort recoveryPort,
            WorkSessionRecoveryExecutionPort recoveryExecution) {
        if (recoveryPort == null) throw new IllegalArgumentException("WORK_SESSION_RECOVERY_PORT_REQUIRED");
        if (recoveryExecution == null) {
            throw new IllegalArgumentException("WORK_SESSION_RECOVERY_EXECUTION_PORT_REQUIRED");
        }
        this.recoveryPort = recoveryPort;
        this.recoveryExecution = recoveryExecution;
    }

    public List<WorkSessionRecoveryPort.RecoveryDecision> recoverExpiredLeases(int limit) {
        validateLimit(limit);
        List<WorkSessionRecoveryPort.RecoveryDecision> decisions = recoveryPort.recoverExpiredLeases(limit);
        return decisions == null ? List.of() : List.copyOf(decisions);
    }

    public RecoveryBatchOutcome recoverAndExecuteExpiredLeases(int limit) {
        List<WorkSessionRecoveryPort.RecoveryDecision> decisions = recoverExpiredLeases(limit);
        List<WorkSessionRecoveryExecutionPort.RecoveryExecutionOutcome> executions = new ArrayList<>();
        for (WorkSessionRecoveryPort.RecoveryDecision decision : decisions) {
            if (!"RECOVERABLE".equalsIgnoreCase(decision.status())) continue;
            WorkSessionRecoveryExecutionPort.RecoveryExecutionOutcome outcome;
            try {
                outcome = recoveryExecution.execute(decision);
            } catch (RuntimeException error) {
                outcome = WorkSessionRecoveryExecutionPort.RecoveryExecutionOutcome.failed(
                        decision,
                        failureReason(error));
            }
            if (outcome == null) {
                outcome = WorkSessionRecoveryExecutionPort.RecoveryExecutionOutcome.failed(
                        decision,
                        "WORK_SESSION_RECOVERY_EXECUTION_RESULT_REQUIRED");
            }
            executions.add(outcome);
        }
        return new RecoveryBatchOutcome(decisions, List.copyOf(executions));
    }

    private void validateLimit(int limit) {
        if (limit <= 0 || limit > 1000) {
            throw new IllegalArgumentException("WORK_SESSION_RECOVERY_LIMIT_INVALID");
        }
    }

    private String failureReason(RuntimeException error) {
        String message = error == null ? "" : error.getMessage();
        if (message == null || message.isBlank()) {
            return "WORK_SESSION_RECOVERY_EXECUTION_FAILED";
        }
        String normalized = message.trim().toUpperCase().replaceAll("[^A-Z0-9_]+", "_");
        if (normalized.length() > 96) normalized = normalized.substring(0, 96);
        return normalized.isBlank() ? "WORK_SESSION_RECOVERY_EXECUTION_FAILED" : normalized;
    }

    public record RecoveryBatchOutcome(
            List<WorkSessionRecoveryPort.RecoveryDecision> decisions,
            List<WorkSessionRecoveryExecutionPort.RecoveryExecutionOutcome> executions) {

        public RecoveryBatchOutcome {
            decisions = decisions == null ? List.of() : List.copyOf(decisions);
            executions = executions == null ? List.of() : List.copyOf(executions);
        }
    }
}
