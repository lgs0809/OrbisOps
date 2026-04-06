package cn.lgs.orbisops.trigger.application.worksession;

import cn.lgs.orbisops.application.worksession.ExecuteWorkSessionUseCase;
import cn.lgs.orbisops.application.worksession.WorkSessionLifecyclePort;
import cn.lgs.orbisops.application.worksession.WorkSessionPreparation;
import cn.lgs.orbisops.application.worksession.WorkSessionRecoveryExecutionPort;
import cn.lgs.orbisops.application.worksession.WorkSessionRecoveryPort;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatResponse;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionRunAdapter;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsWorkSessionRecoveryExecutionAdapterTest {

    @Test
    void recoverableDecisionMustRebuildRequestAndExecuteNormalLifecycle() {
        OpsWorkSessionRunAdapter runs = mock(OpsWorkSessionRunAdapter.class);
        OpsAgentChatRequest request = new OpsAgentChatRequest();
        request.setRunId("run-1");
        request.setProjectId("project-1");
        request.setMetadata(Map.of("contextBundleId", "bundle-1"));
        when(runs.recoveryRequest("run-1", "project-1", "attempt-1")).thenReturn(request);
        AtomicReference<OpsAgentChatRequest> executed = new AtomicReference<>();
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> useCase =
                new ExecuteWorkSessionUseCase<>(new RecordingLifecycle(executed));
        OpsWorkSessionRecoveryExecutionAdapter adapter =
                new OpsWorkSessionRecoveryExecutionAdapter(runs, useCase, Runnable::run);
        WorkSessionRecoveryPort.RecoveryDecision decision = new WorkSessionRecoveryPort.RecoveryDecision(
                "run-1", "project-1", "attempt-1", "RECOVERABLE", "LEASE_EXPIRED_SAFE_CHECKPOINT");

        WorkSessionRecoveryExecutionPort.RecoveryExecutionOutcome outcome = adapter.execute(decision);

        assertEquals("SCHEDULED", outcome.status());
        assertEquals(request, executed.get());
        assertEquals(true, request.getMetadata().get("automaticRecovery"));
        assertEquals("LEASE_EXPIRED_SAFE_CHECKPOINT", request.getMetadata().get("recoveryReasonCode"));
        assertEquals("attempt-1", request.getMetadata().get("recoveryExpiredAttemptId"));
        assertEquals("bundle-1", request.getMetadata().get("contextBundleId"));
    }

    @Test
    void reviewDecisionMustNeverRebuildOrExecuteRequest() {
        OpsWorkSessionRunAdapter runs = mock(OpsWorkSessionRunAdapter.class);
        AtomicReference<OpsAgentChatRequest> executed = new AtomicReference<>();
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> useCase =
                new ExecuteWorkSessionUseCase<>(new RecordingLifecycle(executed));
        OpsWorkSessionRecoveryExecutionAdapter adapter =
                new OpsWorkSessionRecoveryExecutionAdapter(runs, useCase, Runnable::run);
        WorkSessionRecoveryPort.RecoveryDecision decision = new WorkSessionRecoveryPort.RecoveryDecision(
                "run-1", "project-1", "attempt-1", "RECOVERY_REVIEW_REQUIRED", "SIDE_EFFECT_AMBIGUOUS");

        WorkSessionRecoveryExecutionPort.RecoveryExecutionOutcome outcome = adapter.execute(decision);

        assertEquals("SKIPPED", outcome.status());
        assertNull(executed.get());
        verify(runs, never()).recoveryRequest("run-1", "project-1", "attempt-1");
    }

    @Test
    void asynchronousRecoveryFailureMustFinalizeRunInsteadOfEscapingWorkerThread() {
        OpsWorkSessionRunAdapter runs = mock(OpsWorkSessionRunAdapter.class);
        OpsAgentChatRequest request = new OpsAgentChatRequest();
        request.setRunId("run-1");
        request.setProjectId("project-1");
        request.setMetadata(Map.of());
        when(runs.recoveryRequest("run-1", "project-1", "attempt-1")).thenReturn(request);
        ExecuteWorkSessionUseCase<OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> useCase =
                new ExecuteWorkSessionUseCase<>(new FailingPreparationLifecycle());
        OpsWorkSessionRecoveryExecutionAdapter adapter =
                new OpsWorkSessionRecoveryExecutionAdapter(runs, useCase, Runnable::run);
        WorkSessionRecoveryPort.RecoveryDecision decision = new WorkSessionRecoveryPort.RecoveryDecision(
                "run-1", "project-1", "attempt-1", "RECOVERABLE", "LEASE_EXPIRED_SAFE_CHECKPOINT");

        WorkSessionRecoveryExecutionPort.RecoveryExecutionOutcome outcome = adapter.execute(decision);

        assertEquals("SCHEDULED", outcome.status());
        verify(runs).finish(request, "FAILED",
                "WORK_SESSION_RECOVERY_EXECUTION_FAILED:RECOVERED_AUTHORITY_CONTEXT_EXPIRED");
    }

    private static final class RecordingLifecycle implements
            WorkSessionLifecyclePort<OpsAgentChatRequest, OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> {
        private final AtomicReference<OpsAgentChatRequest> executed;

        private RecordingLifecycle(AtomicReference<OpsAgentChatRequest> executed) {
            this.executed = executed;
        }

        @Override
        public WorkSessionPreparation<OpsAgentChatRequest, OpsAgentChatResponse> prepare(
                OpsAgentChatRequest request, Consumer<OpsRuntimeEvent> eventSink) {
            return WorkSessionPreparation.ready(request);
        }

        @Override
        public void execute(OpsAgentChatRequest context, Consumer<OpsRuntimeEvent> eventSink) {
            executed.set(context);
        }

        @Override
        public OpsAgentChatResponse succeed(
                OpsAgentChatRequest context, Consumer<OpsRuntimeEvent> eventSink) {
            return new OpsAgentChatResponse();
        }

        @Override
        public boolean isCancellation(RuntimeException error) {
            return false;
        }

        @Override
        public OpsAgentChatResponse cancel(
                OpsAgentChatRequest context, RuntimeException error, Consumer<OpsRuntimeEvent> eventSink) {
            return new OpsAgentChatResponse();
        }

        @Override
        public void fail(
                OpsAgentChatRequest context, RuntimeException error, Consumer<OpsRuntimeEvent> eventSink) {
        }

        @Override
        public Map<String, Object> capabilities() {
            return Map.of();
        }
    }

    private static final class FailingPreparationLifecycle implements
            WorkSessionLifecyclePort<OpsAgentChatRequest, OpsAgentChatRequest, OpsAgentChatResponse, OpsRuntimeEvent> {

        @Override
        public WorkSessionPreparation<OpsAgentChatRequest, OpsAgentChatResponse> prepare(
                OpsAgentChatRequest request, Consumer<OpsRuntimeEvent> eventSink) {
            throw new SecurityException("RECOVERED_AUTHORITY_CONTEXT_EXPIRED");
        }

        @Override public void execute(OpsAgentChatRequest context, Consumer<OpsRuntimeEvent> eventSink) { }
        @Override public OpsAgentChatResponse succeed(OpsAgentChatRequest context, Consumer<OpsRuntimeEvent> eventSink) { return null; }
        @Override public boolean isCancellation(RuntimeException error) { return false; }
        @Override public OpsAgentChatResponse cancel(OpsAgentChatRequest context, RuntimeException error, Consumer<OpsRuntimeEvent> eventSink) { return null; }
        @Override public void fail(OpsAgentChatRequest context, RuntimeException error, Consumer<OpsRuntimeEvent> eventSink) { }
        @Override public Map<String, Object> capabilities() { return Map.of(); }
    }
}
