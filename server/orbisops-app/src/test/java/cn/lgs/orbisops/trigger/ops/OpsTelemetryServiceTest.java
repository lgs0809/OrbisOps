package cn.lgs.orbisops.trigger.ops;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsTelemetryServiceTest {

    @Test
    void noRegistryKeepsLocalCountersAndReportsMetricsDisabled() {
        OpsTelemetryService telemetry = new OpsTelemetryService();

        telemetry.recordSubmitted();
        telemetry.recordStarted();
        telemetry.recordSucceeded(50L);
        telemetry.recordStarted();
        telemetry.recordFailed(-10L);
        telemetry.recordCanceled();
        Map<String, Object> snapshot = telemetry.snapshot();

        assertEquals(1L, snapshot.get("submittedRuns"));
        assertEquals(0L, snapshot.get("runningRuns"));
        assertEquals(1L, snapshot.get("succeededRuns"));
        assertEquals(1L, snapshot.get("failedRuns"));
        assertEquals(1L, snapshot.get("canceledRuns"));
        assertEquals(25L, snapshot.get("avgDurationMs"));
        assertFalse(Boolean.TRUE.equals(snapshot.get("prometheusMetricsEnabled")));
    }

    @Test
    void constructorBoundRegistryPublishesBaseRuntimeAndNodeMeters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OpsTelemetryService telemetry = new OpsTelemetryService(registry);

        telemetry.recordSubmitted();
        telemetry.recordStarted();
        telemetry.recordSucceeded(20L);
        telemetry.recordRuntimeStarted("agent-1", "graph");
        telemetry.recordRuntimeSucceeded("agent-1", "graph", 30L);
        telemetry.recordRuntimeNode("agent-1", "graph", "TOOL", "SUCCEEDED", 10L);

        assertTrue(Boolean.TRUE.equals(
                telemetry.snapshot().get("prometheusMetricsEnabled")));
        assertEquals(1D,
                registry.get("ops_ai_runs_submitted_total").counter().count());
        assertEquals(1D,
                registry.get("ops_ai_runs_succeeded_total").counter().count());
        assertEquals(20D,
                registry.get("ops_ai_run_duration").timer().totalTime(
                        java.util.concurrent.TimeUnit.MILLISECONDS));
        assertEquals(1D,
                registry.get("ops_agent_runtime_runs_total")
                        .tag("status", "started")
                        .counter()
                        .count());
        assertNotNull(registry.get("ops_agent_runtime_node_duration")
                .tag("node_type", "TOOL")
                .timer());
    }
}
