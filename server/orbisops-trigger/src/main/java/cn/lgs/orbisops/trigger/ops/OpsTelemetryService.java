package cn.lgs.orbisops.trigger.ops;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Lightweight in-process telemetry with an optional constructor-bound Micrometer adapter. */
@Service
public class OpsTelemetryService {

    private final AtomicLong submittedRuns = new AtomicLong();
    private final AtomicLong runningRuns = new AtomicLong();
    private final AtomicLong succeededRuns = new AtomicLong();
    private final AtomicLong failedRuns = new AtomicLong();
    private final AtomicLong canceledRuns = new AtomicLong();
    private final AtomicLong totalDurationMs = new AtomicLong();
    private final OpsTelemetryMeters meters;

    public OpsTelemetryService() {
        this((MeterRegistry) null);
    }

    @Autowired
    public OpsTelemetryService(
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(meterRegistryProvider == null
                ? null
                : meterRegistryProvider.getIfAvailable());
    }

    OpsTelemetryService(MeterRegistry meterRegistry) {
        this.meters = new OpsTelemetryMeters(meterRegistry, runningRuns);
    }

    public void recordSubmitted() {
        submittedRuns.incrementAndGet();
        meters.submitted();
    }

    public void recordStarted() {
        runningRuns.incrementAndGet();
    }

    public void recordSucceeded(long durationMs) {
        runningRuns.updateAndGet(value -> Math.max(0, value - 1));
        succeededRuns.incrementAndGet();
        totalDurationMs.addAndGet(Math.max(0, durationMs));
        meters.succeeded(durationMs);
    }

    public void recordFailed(long durationMs) {
        runningRuns.updateAndGet(value -> Math.max(0, value - 1));
        failedRuns.incrementAndGet();
        totalDurationMs.addAndGet(Math.max(0, durationMs));
        meters.failed(durationMs);
    }

    public void recordCanceled() {
        runningRuns.updateAndGet(value -> Math.max(0, value - 1));
        canceledRuns.incrementAndGet();
        meters.canceled();
    }

    public Map<String, Object> snapshot() {
        long completed = succeededRuns.get() + failedRuns.get();
        Map<String, Object> telemetry = new LinkedHashMap<>();
        telemetry.put("submittedRuns", submittedRuns.get());
        telemetry.put("runningRuns", runningRuns.get());
        telemetry.put("succeededRuns", succeededRuns.get());
        telemetry.put("failedRuns", failedRuns.get());
        telemetry.put("canceledRuns", canceledRuns.get());
        telemetry.put(
                "avgDurationMs",
                completed == 0 ? 0 : totalDurationMs.get() / completed);
        telemetry.put("prometheusMetricsEnabled", meters.enabled());
        return telemetry;
    }

    public void recordRuntimeStarted(String agentId, String engine) {
        meters.runtimeStarted(agentId, engine);
    }

    public void recordRuntimeSucceeded(
            String agentId,
            String engine,
            long durationMs) {
        meters.runtimeCompleted(agentId, engine, "succeeded", durationMs);
    }

    public void recordRuntimeFailed(
            String agentId,
            String engine,
            long durationMs) {
        meters.runtimeCompleted(agentId, engine, "failed", durationMs);
    }

    public void recordRuntimeCanceled(
            String agentId,
            String engine,
            long durationMs) {
        meters.runtimeCompleted(agentId, engine, "canceled", durationMs);
    }

    public void recordRuntimeNode(
            String agentId,
            String engine,
            String nodeType,
            String status,
            long durationMs) {
        meters.runtimeNode(agentId, engine, nodeType, status, durationMs);
    }
}
