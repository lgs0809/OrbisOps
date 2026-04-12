package cn.lgs.orbisops.application.analysis;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/** Application process manager for asynchronous analysis admission, execution and cancellation. */
public final class AsyncAnalysisRunProcessManager<Q, R> {

    private final AsyncAnalysisRunStorePort<Q, R> storePort;
    private final AsyncAnalysisExecutionPort executionPort;
    private final AsyncAnalysisCancellationPort cancellationPort;
    private final AsyncAnalysisRequestPort<Q> requestPort;
    private final AsyncAnalysisTelemetryPort telemetryPort;
    private final AsyncAnalysisAuditPort<Q, R> auditPort;
    private final AsyncAnalysisOutcomePort<Q, R> outcomePort;
    private final Supplier<String> idSupplier;
    private final Clock clock;

    public AsyncAnalysisRunProcessManager(
            AsyncAnalysisRunStorePort<Q, R> storePort,
            AsyncAnalysisExecutionPort executionPort,
            AsyncAnalysisCancellationPort cancellationPort,
            AsyncAnalysisRequestPort<Q> requestPort,
            AsyncAnalysisTelemetryPort telemetryPort,
            AsyncAnalysisAuditPort<Q, R> auditPort,
            AsyncAnalysisOutcomePort<Q, R> outcomePort,
            Supplier<String> idSupplier,
            Clock clock) {
        this.storePort = required(storePort, "ASYNC_ANALYSIS_STORE_PORT_REQUIRED");
        this.executionPort = required(executionPort, "ASYNC_ANALYSIS_EXECUTION_PORT_REQUIRED");
        this.cancellationPort = required(cancellationPort, "ASYNC_ANALYSIS_CANCELLATION_PORT_REQUIRED");
        this.requestPort = required(requestPort, "ASYNC_ANALYSIS_REQUEST_PORT_REQUIRED");
        this.telemetryPort = required(telemetryPort, "ASYNC_ANALYSIS_TELEMETRY_PORT_REQUIRED");
        this.auditPort = required(auditPort, "ASYNC_ANALYSIS_AUDIT_PORT_REQUIRED");
        this.outcomePort = required(outcomePort, "ASYNC_ANALYSIS_OUTCOME_PORT_REQUIRED");
        this.idSupplier = required(idSupplier, "ASYNC_ANALYSIS_ID_SUPPLIER_REQUIRED");
        this.clock = required(clock, "ASYNC_ANALYSIS_CLOCK_REQUIRED");
    }

    public AsyncAnalysisRun<Q, R> submit(Q request, Function<Q, R> analysisFunction) {
        if (request == null) {
            throw new IllegalArgumentException("ASYNC_ANALYSIS_REQUEST_REQUIRED");
        }
        if (analysisFunction == null) {
            throw new IllegalArgumentException("ASYNC_ANALYSIS_FUNCTION_REQUIRED");
        }
        executionPort.assertCapacity();
        String runId = requiredText(idSupplier.get(), "ASYNC_ANALYSIS_RUN_ID_REQUIRED");
        requestPort.bindRunId(request, runId);
        AsyncAnalysisRun<Q, R> pending = AsyncAnalysisRun.pending(runId, request, now());
        storePort.save(pending);
        telemetryPort.submitted();
        executionPort.submit(runId, () -> execute(runId, analysisFunction));
        return pending;
    }

    public Optional<AsyncAnalysisRun<Q, R>> get(String runId) {
        return storePort.get(runId);
    }

    public List<AsyncAnalysisRun<Q, R>> list(int limit) {
        List<AsyncAnalysisRun<Q, R>> values = storePort.list(Math.max(1, Math.min(limit, 100)));
        return values == null || values.isEmpty() ? List.of() : List.copyOf(values);
    }

    public int activeCountByProject(String projectId) {
        return storePort.activeCountByProject(projectId == null ? "" : projectId.trim());
    }

    public boolean cancel(String runId) {
        Optional<AsyncAnalysisRun<Q, R>> current = storePort.get(runId);
        if (current.isEmpty() || current.get().terminal()) {
            return false;
        }
        AsyncAnalysisRun<Q, R> run = current.get();
        cancellationPort.markCanceled(runId);
        executionPort.cancel(runId);
        AsyncAnalysisRun<Q, R> canceled = run.canceled(now());
        storePort.save(canceled);
        outcomePort.canceled(canceled.request(), runId);
        telemetryPort.canceled();
        return true;
    }

    private void execute(String runId, Function<Q, R> analysisFunction) {
        long startedAt = clock.millis();
        AsyncAnalysisRun<Q, R> run = storePort.get(runId).orElse(null);
        if (run == null || run.terminal() || cancellationPort.isCanceled(runId)) {
            finish(runId);
            return;
        }
        run = run.running(now());
        storePort.save(run);
        telemetryPort.started();
        try {
            cancellationPort.assertNotCanceled(runId);
            R response = analysisFunction.apply(run.request());
            if (executionPort.interrupted() || canceled(runId)) {
                return;
            }
            long duration = duration(startedAt);
            AsyncAnalysisRun<Q, R> succeeded = run.succeeded(response, duration, now());
            storePort.save(succeeded);
            telemetryPort.succeeded(duration);
            auditPort.succeeded(succeeded.request(), response, duration);
            outcomePort.succeeded(succeeded.request(), runId, response);
        } catch (AsyncAnalysisCanceledException error) {
            AsyncAnalysisRun<Q, R> canceled = run.canceled(now());
            storePort.save(canceled);
        } catch (Exception error) {
            if (executionPort.interrupted() || canceled(runId)) {
                return;
            }
            long duration = duration(startedAt);
            AsyncAnalysisRun<Q, R> failed = run.failed(error.getMessage(), duration, now());
            storePort.save(failed);
            telemetryPort.failed(duration);
            auditPort.failed(failed.request(), error, duration);
            outcomePort.failed(failed.request(), runId, error.getMessage());
        } finally {
            finish(runId);
        }
    }

    private boolean canceled(String runId) {
        return cancellationPort.isCanceled(runId)
                || storePort.get(runId)
                .map(run -> run.status() != null && "CANCELED".equals(run.status().name()))
                .orElse(false);
    }

    private void finish(String runId) {
        executionPort.finished(runId);
        cancellationPort.markFinished(runId);
    }

    private long duration(long startedAt) {
        return Math.max(0L, clock.millis() - startedAt);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private <T> T required(T value, String error) {
        if (value == null) {
            throw new IllegalArgumentException(error);
        }
        return value;
    }

    private String requiredText(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }
}
