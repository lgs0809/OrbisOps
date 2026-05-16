package cn.lgs.orbisops.trigger.ops.runtime;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Low-cardinality MCP runtime SLI aggregation and Micrometer projection. */
@Component
public final class OpsMcpRuntimeSloTelemetry {

    private final MeterRegistry registry;
    private final AtomicLong succeeded = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong circuitRejected = new AtomicLong();
    private final AtomicLong totalDurationMs = new AtomicLong();
    private final AtomicLong maxDurationMs = new AtomicLong();
    private final AtomicLong openCircuits = new AtomicLong();

    @Autowired
    public OpsMcpRuntimeSloTelemetry(ObjectProvider<MeterRegistry> registryProvider) {
        this(registryProvider == null ? null : registryProvider.getIfAvailable());
    }

    OpsMcpRuntimeSloTelemetry(MeterRegistry registry) {
        this.registry = registry;
        if (registry != null) {
            Gauge.builder("orbisops.mcp.circuits.open", openCircuits, AtomicLong::get)
                    .description("Current number of open MCP runtime circuits")
                    .register(registry);
        }
    }

    public void success(long durationMs) {
        succeeded.incrementAndGet();
        duration("succeeded", durationMs);
    }

    public void failure(long durationMs) {
        failed.incrementAndGet();
        duration("failed", durationMs);
    }

    public void circuitRejected() {
        circuitRejected.incrementAndGet();
        if (registry != null) {
            registry.counter("orbisops.mcp.invocations", "outcome", "circuit_rejected").increment();
        }
    }

    public void circuitOpened() {
        openCircuits.incrementAndGet();
        if (registry != null) registry.counter("orbisops.mcp.circuit.transitions", "state", "open").increment();
    }

    public void circuitClosed() {
        openCircuits.updateAndGet(value -> Math.max(0L, value - 1L));
        if (registry != null) registry.counter("orbisops.mcp.circuit.transitions", "state", "closed").increment();
    }

    public Map<String, Object> snapshot() {
        long successCount = succeeded.get();
        long failureCount = failed.get();
        long completed = successCount + failureCount;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("succeededCalls", successCount);
        result.put("failedCalls", failureCount);
        result.put("circuitRejectedCalls", circuitRejected.get());
        result.put("openCircuits", openCircuits.get());
        result.put("successRate", completed == 0L ? 1.0d : (double) successCount / completed);
        result.put("avgDurationMs", completed == 0L ? 0L : totalDurationMs.get() / completed);
        result.put("maxDurationMs", maxDurationMs.get());
        result.put("sloStatus", sloStatus(completed, failureCount));
        return Map.copyOf(result);
    }

    private void duration(String outcome, long durationMs) {
        long bounded = Math.max(0L, durationMs);
        totalDurationMs.addAndGet(bounded);
        maxDurationMs.accumulateAndGet(bounded, Math::max);
        if (registry != null) {
            registry.counter("orbisops.mcp.invocations", "outcome", outcome).increment();
            Timer.builder("orbisops.mcp.invocation.duration")
                    .tag("outcome", outcome)
                    .register(registry)
                    .record(bounded, TimeUnit.MILLISECONDS);
        }
    }

    private String sloStatus(long completed, long failureCount) {
        if (openCircuits.get() > 0L) return "DEGRADED";
        if (completed < 10L) return "WARMING_UP";
        double failureRate = (double) failureCount / completed;
        if (failureRate >= 0.50d) return "BREACHED";
        if (failureRate >= 0.10d) return "DEGRADED";
        return "HEALTHY";
    }
}
