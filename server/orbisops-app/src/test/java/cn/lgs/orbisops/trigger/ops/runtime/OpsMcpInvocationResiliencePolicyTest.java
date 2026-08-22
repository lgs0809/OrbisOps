package cn.lgs.orbisops.trigger.ops.runtime;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMcpInvocationResiliencePolicyTest {

    @Test
    void failureBudgetMustOpenCircuitAndAllowOneHalfOpenProbe() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-03T08:00:00Z"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OpsMcpRuntimeSloTelemetry telemetry = new OpsMcpRuntimeSloTelemetry(registry);
        OpsMcpInvocationResiliencePolicy policy = new OpsMcpInvocationResiliencePolicy(
                2, Duration.ofSeconds(10), clock, telemetry);
        OpsMcpServerConfig config = config();

        OpsMcpInvocationResiliencePolicy.Permit first = policy.acquire(config, "query");
        assertEquals(false, policy.failed(first, 12L));
        OpsMcpInvocationResiliencePolicy.Permit second = policy.acquire(config, "query");
        assertTrue(policy.failed(second, 18L));

        IllegalStateException open = assertThrows(
                IllegalStateException.class,
                () -> policy.acquire(config, "query"));
        assertTrue(open.getMessage().startsWith("MCP_CIRCUIT_OPEN:"));

        clock.advance(Duration.ofSeconds(11));
        OpsMcpInvocationResiliencePolicy.Permit probe = policy.acquire(config, "query");
        assertTrue(probe.halfOpenProbe());
        IllegalStateException busy = assertThrows(
                IllegalStateException.class,
                () -> policy.acquire(config, "query"));
        assertTrue(busy.getMessage().startsWith("MCP_CIRCUIT_HALF_OPEN_BUSY:"));

        policy.succeeded(probe, 5L);
        assertEquals(false, policy.acquire(config, "query").halfOpenProbe());

        Map<String, Object> snapshot = telemetry.snapshot();
        assertEquals(1L, snapshot.get("succeededCalls"));
        assertEquals(2L, snapshot.get("failedCalls"));
        assertEquals(2L, snapshot.get("circuitRejectedCalls"));
        assertEquals(0L, snapshot.get("openCircuits"));
        assertEquals(2.0d,
                registry.get("orbisops.mcp.invocations")
                        .tag("outcome", "circuit_rejected")
                        .counter()
                        .count());
    }

    @Test
    void stalePermitMustBeFencedAfterCircuitGenerationChanges() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-03T08:00:00Z"));
        OpsMcpRuntimeSloTelemetry telemetry = new OpsMcpRuntimeSloTelemetry((MeterRegistry) null);
        OpsMcpInvocationResiliencePolicy policy = new OpsMcpInvocationResiliencePolicy(
                1, Duration.ofSeconds(5), clock, telemetry);
        OpsMcpInvocationResiliencePolicy.Permit stale = policy.acquire(config(), "query");

        assertTrue(policy.failed(stale, 1L));

        IllegalStateException fenced = assertThrows(
                IllegalStateException.class,
                () -> policy.succeeded(stale, 1L));
        assertTrue(fenced.getMessage().startsWith("MCP_RESILIENCE_PERMIT_FENCED:"));
    }

    private OpsMcpServerConfig config() {
        return OpsMcpServerConfig.builder()
                .name("ops-mcp")
                .mcpId("mcp-1")
                .build();
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
