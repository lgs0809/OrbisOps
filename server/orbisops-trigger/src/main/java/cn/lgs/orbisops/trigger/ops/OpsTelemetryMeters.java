package cn.lgs.orbisops.trigger.ops;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Constructor-bound Micrometer adapter for operations runtime telemetry. */
final class OpsTelemetryMeters {

    private final MeterRegistry registry;
    private final Counter submittedCounter;
    private final Counter succeededCounter;
    private final Counter failedCounter;
    private final Counter canceledCounter;
    private final Timer durationTimer;

    OpsTelemetryMeters(MeterRegistry registry, AtomicLong runningRuns) {
        this.registry = registry;
        if (registry == null) {
            this.submittedCounter = null;
            this.succeededCounter = null;
            this.failedCounter = null;
            this.canceledCounter = null;
            this.durationTimer = null;
            return;
        }
        this.submittedCounter = Counter.builder("ops_ai_runs_submitted_total")
                .description("Total submitted operations AI analysis runs")
                .register(registry);
        this.succeededCounter = Counter.builder("ops_ai_runs_succeeded_total")
                .description("Total succeeded operations AI analysis runs")
                .register(registry);
        this.failedCounter = Counter.builder("ops_ai_runs_failed_total")
                .description("Total failed operations AI analysis runs")
                .register(registry);
        this.canceledCounter = Counter.builder("ops_ai_runs_canceled_total")
                .description("Total canceled operations AI analysis runs")
                .register(registry);
        this.durationTimer = Timer.builder("ops_ai_run_duration")
                .description("Operations AI analysis run duration")
                .publishPercentileHistogram()
                .register(registry);
        Gauge.builder("ops_ai_runs_running", runningRuns, AtomicLong::get)
                .description("Currently running operations AI analysis runs")
                .register(registry);
    }

    boolean enabled() {
        return registry != null;
    }

    void submitted() {
        increment(submittedCounter);
    }

    void succeeded(long durationMs) {
        increment(succeededCounter);
        duration(durationMs);
    }

    void failed(long durationMs) {
        increment(failedCounter);
        duration(durationMs);
    }

    void canceled() {
        increment(canceledCounter);
    }

    void runtimeStarted(String agentId, String engine) {
        increment(runtimeCounter(agentId, engine, "started"));
    }

    void runtimeCompleted(
            String agentId,
            String engine,
            String status,
            long durationMs) {
        increment(runtimeCounter(agentId, engine, status));
        Timer timer = runtimeTimer(
                "ops_agent_runtime_duration",
                "Operations agent runtime duration",
                agentId,
                engine,
                status,
                "",
                "",
                true);
        record(timer, durationMs);
    }

    void runtimeNode(
            String agentId,
            String engine,
            String nodeType,
            String status,
            long durationMs) {
        if (registry == null) {
            return;
        }
        Counter.builder("ops_agent_runtime_nodes_total")
                .description("Total executed operations agent runtime nodes")
                .tag("agent_id", tag(agentId))
                .tag("engine", tag(engine))
                .tag("node_type", tag(nodeType))
                .tag("status", tag(status))
                .register(registry)
                .increment();
        Timer timer = runtimeTimer(
                "ops_agent_runtime_node_duration",
                "Operations agent runtime node duration",
                agentId,
                engine,
                status,
                "node_type",
                nodeType,
                false);
        record(timer, durationMs);
    }

    private Counter runtimeCounter(
            String agentId,
            String engine,
            String status) {
        if (registry == null) {
            return null;
        }
        return Counter.builder("ops_agent_runtime_runs_total")
                .description("Total operations agent runtime runs")
                .tag("agent_id", tag(agentId))
                .tag("engine", tag(engine))
                .tag("status", tag(status))
                .register(registry);
    }

    private Timer runtimeTimer(
            String metric,
            String description,
            String agentId,
            String engine,
            String status,
            String extraTag,
            String extraValue,
            boolean percentileHistogram) {
        if (registry == null) {
            return null;
        }
        Timer.Builder builder = Timer.builder(metric)
                .description(description)
                .tag("agent_id", tag(agentId))
                .tag("engine", tag(engine))
                .tag("status", tag(status));
        if (percentileHistogram) {
            builder.publishPercentileHistogram();
        }
        if (extraTag != null && !extraTag.isBlank()) {
            builder.tag(extraTag, tag(extraValue));
        }
        return builder.register(registry);
    }

    private void duration(long durationMs) {
        record(durationTimer, durationMs);
    }

    private void increment(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }

    private void record(Timer timer, long durationMs) {
        if (timer != null) {
            timer.record(Math.max(0, durationMs), TimeUnit.MILLISECONDS);
        }
    }

    private String tag(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
