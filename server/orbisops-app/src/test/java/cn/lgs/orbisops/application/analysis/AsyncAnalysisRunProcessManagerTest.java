package cn.lgs.orbisops.application.analysis;

import cn.lgs.orbisops.domain.analysis.model.AnalysisRunStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AsyncAnalysisRunProcessManagerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T08:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void submitBindsIdentityPersistsPendingThenSchedulesExecution() {
        MemoryStore store = new MemoryStore();
        CapturingExecution execution = new CapturingExecution();
        TestRequest request = new TestRequest("project-1");
        AsyncAnalysisTelemetryPort telemetry = mock(AsyncAnalysisTelemetryPort.class);
        AsyncAnalysisRunProcessManager<TestRequest, String> manager = manager(
                store, execution, new TestCancellation(), telemetry,
                mock(AsyncAnalysisAuditPort.class), mock(AsyncAnalysisOutcomePort.class));

        AsyncAnalysisRun<TestRequest, String> pending = manager.submit(request, ignored -> "done");

        assertEquals("run-1", request.runId);
        assertEquals(AnalysisRunStatus.PENDING, pending.status());
        assertEquals(AnalysisRunStatus.PENDING, store.get("run-1").orElseThrow().status());
        assertTrue(execution.tasks.containsKey("run-1"));
        verify(telemetry).submitted();
    }

    @Test
    void executionPersistsRunningAndSucceededBeforeAuditAndOutcome() {
        MemoryStore store = new MemoryStore();
        CapturingExecution execution = new CapturingExecution();
        AsyncAnalysisTelemetryPort telemetry = mock(AsyncAnalysisTelemetryPort.class);
        @SuppressWarnings("unchecked")
        AsyncAnalysisAuditPort<TestRequest, String> audit = mock(AsyncAnalysisAuditPort.class);
        @SuppressWarnings("unchecked")
        AsyncAnalysisOutcomePort<TestRequest, String> outcome = mock(AsyncAnalysisOutcomePort.class);
        AsyncAnalysisRunProcessManager<TestRequest, String> manager = manager(
                store, execution, new TestCancellation(), telemetry, audit, outcome);
        TestRequest request = new TestRequest("project-1");

        manager.submit(request, ignored -> "done");
        execution.run("run-1");

        AsyncAnalysisRun<TestRequest, String> succeeded = store.get("run-1").orElseThrow();
        assertEquals(AnalysisRunStatus.SUCCEEDED, succeeded.status());
        assertEquals("done", succeeded.response());
        assertEquals(0L, succeeded.durationMs());
        verify(telemetry).started();
        verify(telemetry).succeeded(0L);
        verify(audit).succeeded(request, "done", 0L);
        verify(outcome).succeeded(request, "run-1", "done");
    }

    @Test
    void failurePersistsTypedFailureAndPublishesAuditAndOutcome() {
        MemoryStore store = new MemoryStore();
        CapturingExecution execution = new CapturingExecution();
        AsyncAnalysisTelemetryPort telemetry = mock(AsyncAnalysisTelemetryPort.class);
        @SuppressWarnings("unchecked")
        AsyncAnalysisAuditPort<TestRequest, String> audit = mock(AsyncAnalysisAuditPort.class);
        @SuppressWarnings("unchecked")
        AsyncAnalysisOutcomePort<TestRequest, String> outcome = mock(AsyncAnalysisOutcomePort.class);
        AsyncAnalysisRunProcessManager<TestRequest, String> manager = manager(
                store, execution, new TestCancellation(), telemetry, audit, outcome);
        TestRequest request = new TestRequest("project-1");
        IllegalStateException failure = new IllegalStateException("analysis failed");

        manager.submit(request, ignored -> { throw failure; });
        execution.run("run-1");

        AsyncAnalysisRun<TestRequest, String> failed = store.get("run-1").orElseThrow();
        assertEquals(AnalysisRunStatus.FAILED, failed.status());
        assertEquals("analysis failed", failed.errorMessage());
        assertNull(failed.response());
        verify(telemetry).failed(0L);
        verify(audit).failed(request, failure, 0L);
        verify(outcome).failed(request, "run-1", "analysis failed");
    }

    @Test
    void lateResultCannotOverwriteCanceledRun() {
        MemoryStore store = new MemoryStore();
        CapturingExecution execution = new CapturingExecution();
        TestCancellation cancellation = new TestCancellation();
        @SuppressWarnings("unchecked")
        AsyncAnalysisOutcomePort<TestRequest, String> outcome = mock(AsyncAnalysisOutcomePort.class);
        AsyncAnalysisRunProcessManager<TestRequest, String> manager = manager(
                store, execution, cancellation, mock(AsyncAnalysisTelemetryPort.class),
                mock(AsyncAnalysisAuditPort.class), outcome);
        TestRequest request = new TestRequest("project-1");
        AtomicReference<Boolean> canceled = new AtomicReference<>(false);

        manager.submit(request, ignored -> {
            canceled.set(manager.cancel("run-1"));
            return "late-result";
        });
        execution.run("run-1");

        assertTrue(canceled.get());
        AsyncAnalysisRun<TestRequest, String> finalRun = store.get("run-1").orElseThrow();
        assertEquals(AnalysisRunStatus.CANCELED, finalRun.status());
        assertNull(finalRun.response());
        verify(outcome).canceled(request, "run-1");
        verify(outcome, never()).succeeded(request, "run-1", "late-result");
    }

    @Test
    void terminalOrMissingRunCannotBeCanceled() {
        MemoryStore store = new MemoryStore();
        CapturingExecution execution = new CapturingExecution();
        AsyncAnalysisRunProcessManager<TestRequest, String> manager = manager(
                store, execution, new TestCancellation(), mock(AsyncAnalysisTelemetryPort.class),
                mock(AsyncAnalysisAuditPort.class), mock(AsyncAnalysisOutcomePort.class));

        assertFalse(manager.cancel("missing"));
        store.save(AsyncAnalysisRun.<TestRequest, String>pending(
                "done", new TestRequest("project-1"), java.time.LocalDateTime.now(CLOCK))
                .succeeded("ok", 1L, java.time.LocalDateTime.now(CLOCK)));
        assertFalse(manager.cancel("done"));
    }

    @Test
    void executorAdmissionFailurePreventsPersistence() {
        MemoryStore store = new MemoryStore();
        CapturingExecution execution = new CapturingExecution();
        execution.capacityFailure = new IllegalStateException("queue full");
        AsyncAnalysisRunProcessManager<TestRequest, String> manager = manager(
                store, execution, new TestCancellation(), mock(AsyncAnalysisTelemetryPort.class),
                mock(AsyncAnalysisAuditPort.class), mock(AsyncAnalysisOutcomePort.class));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> manager.submit(new TestRequest("project-1"), ignored -> "done"));

        assertEquals("queue full", error.getMessage());
        assertTrue(store.values.isEmpty());
    }

    private AsyncAnalysisRunProcessManager<TestRequest, String> manager(
            MemoryStore store,
            CapturingExecution execution,
            TestCancellation cancellation,
            AsyncAnalysisTelemetryPort telemetry,
            AsyncAnalysisAuditPort<TestRequest, String> audit,
            AsyncAnalysisOutcomePort<TestRequest, String> outcome) {
        return new AsyncAnalysisRunProcessManager<>(
                store,
                execution,
                cancellation,
                new AsyncAnalysisRequestPort<>() {
                    @Override public void bindRunId(TestRequest request, String runId) { request.runId = runId; }
                    @Override public String projectId(TestRequest request) { return request.projectId; }
                },
                telemetry,
                audit,
                outcome,
                () -> "run-1",
                CLOCK);
    }

    private static final class TestRequest {
        private final String projectId;
        private String runId;
        private TestRequest(String projectId) { this.projectId = projectId; }
    }

    private static final class MemoryStore implements AsyncAnalysisRunStorePort<TestRequest, String> {
        private final Map<String, AsyncAnalysisRun<TestRequest, String>> values = new LinkedHashMap<>();
        @Override public void save(AsyncAnalysisRun<TestRequest, String> run) { values.put(run.runId(), run); }
        @Override public Optional<AsyncAnalysisRun<TestRequest, String>> get(String runId) { return Optional.ofNullable(values.get(runId)); }
        @Override public List<AsyncAnalysisRun<TestRequest, String>> list(int limit) { return new ArrayList<>(values.values()); }
        @Override public int activeCountByProject(String projectId) {
            return (int) values.values().stream()
                    .filter(run -> run.request().projectId.equals(projectId))
                    .filter(run -> !run.terminal())
                    .count();
        }
    }

    private static final class CapturingExecution implements AsyncAnalysisExecutionPort {
        private final Map<String, Runnable> tasks = new LinkedHashMap<>();
        private RuntimeException capacityFailure;
        @Override public void assertCapacity() { if (capacityFailure != null) throw capacityFailure; }
        @Override public void submit(String runId, Runnable task) { tasks.put(runId, task); }
        @Override public void cancel(String runId) { }
        @Override public boolean interrupted() { return false; }
        @Override public void finished(String runId) { tasks.remove(runId); }
        private void run(String runId) { tasks.get(runId).run(); }
    }

    private static final class TestCancellation implements AsyncAnalysisCancellationPort {
        private final java.util.Set<String> canceled = new java.util.HashSet<>();
        @Override public void markCanceled(String runId) { canceled.add(runId); }
        @Override public boolean isCanceled(String runId) { return canceled.contains(runId); }
        @Override public void assertNotCanceled(String runId) {
            if (isCanceled(runId)) throw new AsyncAnalysisCanceledException(runId);
        }
        @Override public void markFinished(String runId) { }
    }
}
