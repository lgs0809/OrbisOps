package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.application.worksession.WorkSessionProcessManager;
import cn.lgs.orbisops.application.worksession.WorkSessionRecoveryExecutionPort;
import cn.lgs.orbisops.application.worksession.WorkSessionRecoveryPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsWorkSessionRecoveryWorkerTest {

    @Test
    void disabledPolicySkipsRecoveryScan() {
        WorkSessionProcessManager processManager = mock(WorkSessionProcessManager.class);
        OpsWorkSessionRecoveryWorker worker = new OpsWorkSessionRecoveryWorker(
                processManager,
                new OpsWorkSessionRecoveryDecisionReporter(
                        mock(GraphEventApplicationService.class),
                        mock(OpsConfigAuditService.class)),
                new OpsWorkSessionRecoverySettings(false, 25));

        worker.recoverExpiredRuns();

        verify(processManager, never()).recoverAndExecuteExpiredLeases(25);
    }

    @Test
    void reportsRecoveryDecisionAndExecutionOutcome() {
        WorkSessionProcessManager processManager = mock(WorkSessionProcessManager.class);
        GraphEventApplicationService graphEvents = mock(GraphEventApplicationService.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        WorkSessionRecoveryPort.RecoveryDecision recoverable =
                new WorkSessionRecoveryPort.RecoveryDecision(
                        "run-1", "project-1", "attempt-1", "RECOVERABLE", "LEASE_EXPIRED");
        WorkSessionRecoveryPort.RecoveryDecision review =
                new WorkSessionRecoveryPort.RecoveryDecision(
                        "run-2", "project-2", "attempt-2", "RECOVERY_REVIEW_REQUIRED", "STATE_AMBIGUOUS");
        WorkSessionRecoveryExecutionPort.RecoveryExecutionOutcome scheduled =
                WorkSessionRecoveryExecutionPort.RecoveryExecutionOutcome.scheduled(recoverable);
        when(processManager.recoverAndExecuteExpiredLeases(12))
                .thenReturn(new WorkSessionProcessManager.RecoveryBatchOutcome(
                        List.of(recoverable, review), List.of(scheduled)));
        OpsWorkSessionRecoveryWorker worker = new OpsWorkSessionRecoveryWorker(
                processManager,
                new OpsWorkSessionRecoveryDecisionReporter(graphEvents, audit),
                new OpsWorkSessionRecoverySettings(true, 12));

        worker.recoverExpiredRuns();

        verify(processManager).recoverAndExecuteExpiredLeases(12);
        verify(graphEvents).publishRunEvent(
                "run-1", "work-session-recovery", "WORK_SESSION_RECOVERABLE",
                "RECOVERABLE", "LEASE_EXPIRED");
        verify(graphEvents).publishRunEvent(
                "run-2", "work-session-recovery", "WORK_SESSION_RECOVERY_REVIEW_REQUIRED",
                "RECOVERY_REVIEW_REQUIRED", "STATE_AMBIGUOUS");
        verify(graphEvents).publishRunEvent(
                "run-1", "work-session-recovery-execution", "WORK_SESSION_RECOVERY_SCHEDULED",
                "SCHEDULED", "WORK_SESSION_RECOVERY_SCHEDULED");
    }
}
