package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-MCP failure budget and single-probe circuit breaker. */
@Component
public final class OpsMcpInvocationResiliencePolicy {

    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();
    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;
    private final OpsMcpRuntimeSloTelemetry telemetry;

    @Autowired
    public OpsMcpInvocationResiliencePolicy(
            @Value("${orbisops.mcp.resilience.failure-threshold:3}") int failureThreshold,
            @Value("${orbisops.mcp.resilience.open-seconds:30}") long openSeconds,
            OpsMcpRuntimeSloTelemetry telemetry) {
        this(
                failureThreshold,
                Duration.ofSeconds(Math.max(1L, openSeconds)),
                Clock.systemUTC(),
                telemetry);
    }

    OpsMcpInvocationResiliencePolicy(
            int failureThreshold,
            Duration openDuration,
            Clock clock,
            OpsMcpRuntimeSloTelemetry telemetry) {
        if (failureThreshold <= 0 || failureThreshold > 100) {
            throw new IllegalArgumentException("MCP_RESILIENCE_FAILURE_THRESHOLD_INVALID");
        }
        if (openDuration == null || openDuration.isZero() || openDuration.isNegative()) {
            throw new IllegalArgumentException("MCP_RESILIENCE_OPEN_DURATION_INVALID");
        }
        if (clock == null || telemetry == null) {
            throw new IllegalArgumentException("MCP_RESILIENCE_DEPENDENCIES_REQUIRED");
        }
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = clock;
        this.telemetry = telemetry;
    }

    public Permit acquire(OpsMcpServerConfig config, String toolName) {
        String key = key(config, toolName);
        CircuitState state = circuits.computeIfAbsent(key, ignored -> new CircuitState());
        synchronized (state) {
            Instant now = clock.instant();
            if (state.openUntil != null) {
                if (now.isBefore(state.openUntil)) {
                    telemetry.circuitRejected();
                    throw new IllegalStateException("MCP_CIRCUIT_OPEN:" + key);
                }
                if (state.halfOpenInFlight) {
                    telemetry.circuitRejected();
                    throw new IllegalStateException("MCP_CIRCUIT_HALF_OPEN_BUSY:" + key);
                }
                state.halfOpenInFlight = true;
                return new Permit(key, state.generation, true);
            }
            return new Permit(key, state.generation, false);
        }
    }

    public void succeeded(Permit permit, long durationMs) {
        CircuitState state = state(permit);
        synchronized (state) {
            assertPermit(state, permit);
            boolean wasOpen = state.openUntil != null;
            state.consecutiveFailures = 0;
            state.openUntil = null;
            state.halfOpenInFlight = false;
            if (wasOpen) telemetry.circuitClosed();
        }
        telemetry.success(durationMs);
    }

    public boolean failed(Permit permit, long durationMs) {
        CircuitState state = state(permit);
        boolean opened = false;
        synchronized (state) {
            assertPermit(state, permit);
            state.halfOpenInFlight = false;
            state.consecutiveFailures++;
            if (permit.halfOpenProbe() || state.consecutiveFailures >= failureThreshold) {
                boolean wasOpen = state.openUntil != null;
                state.openUntil = clock.instant().plus(openDuration);
                state.generation++;
                state.consecutiveFailures = 0;
                opened = true;
                if (!wasOpen) telemetry.circuitOpened();
            }
        }
        telemetry.failure(durationMs);
        return opened;
    }

    public void neutral(Permit permit) {
        CircuitState state = state(permit);
        synchronized (state) {
            if (state.generation == permit.generation() && permit.halfOpenProbe()) state.halfOpenInFlight = false;
        }
    }

    public Map<String, Object> snapshot() {
        long open = circuits.values().stream().filter(state -> {
            synchronized (state) {
                return state.openUntil != null;
            }
        }).count();
        return Map.of(
                "trackedCircuits", circuits.size(),
                "openCircuits", open,
                "failureThreshold", failureThreshold,
                "openDurationSeconds", openDuration.toSeconds());
    }

    private CircuitState state(Permit permit) {
        if (permit == null) throw new IllegalArgumentException("MCP_RESILIENCE_PERMIT_REQUIRED");
        CircuitState state = circuits.get(permit.key());
        if (state == null) throw new IllegalStateException("MCP_RESILIENCE_STATE_MISSING");
        return state;
    }

    private void assertPermit(CircuitState state, Permit permit) {
        if (state.generation != permit.generation()) {
            throw new IllegalStateException("MCP_RESILIENCE_PERMIT_FENCED:" + permit.key());
        }
    }

    private String key(OpsMcpServerConfig config, String toolName) {
        if (config == null) throw new IllegalArgumentException("MCP_SERVER_CONFIG_REQUIRED");
        String server = value(config.getMcpId());
        if (server.isBlank()) server = value(config.getName());
        if (server.isBlank()) throw new IllegalArgumentException("MCP_SERVER_ID_REQUIRED");
        String tool = value(toolName);
        String project = value(config.getProjectId());
        return project + "/" + (tool.isBlank() ? server : server + "/" + tool);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record Permit(String key, long generation, boolean halfOpenProbe) {
        public Permit {
            if (key == null || key.isBlank() || generation < 0L) {
                throw new IllegalArgumentException("MCP_RESILIENCE_PERMIT_INVALID");
            }
        }
    }

    private static final class CircuitState {
        private int consecutiveFailures;
        private Instant openUntil;
        private boolean halfOpenInFlight;
        private long generation;
    }
}
