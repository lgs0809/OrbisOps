package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpInvocationResiliencePolicy;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpRuntimeSloTelemetry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Actuator health projection for MCP runtime SLO and circuit state. */
@Component("opsMcpRuntime")
public final class OpsMcpRuntimeHealthIndicator implements HealthIndicator {

    private final OpsMcpRuntimeSloTelemetry telemetry;
    private final OpsMcpInvocationResiliencePolicy resilience;

    public OpsMcpRuntimeHealthIndicator(
            OpsMcpRuntimeSloTelemetry telemetry,
            OpsMcpInvocationResiliencePolicy resilience) {
        if (telemetry == null || resilience == null) {
            throw new IllegalArgumentException("MCP_RUNTIME_HEALTH_DEPENDENCIES_REQUIRED");
        }
        this.telemetry = telemetry;
        this.resilience = resilience;
    }

    @Override
    public Health health() {
        Map<String, Object> slo = telemetry.snapshot();
        Map<String, Object> circuit = resilience.snapshot();
        String status = String.valueOf(slo.getOrDefault("sloStatus", "WARMING_UP"));
        long openCircuits = number(circuit.get("openCircuits"));
        Health.Builder builder;
        if ("BREACHED".equals(status)) {
            builder = Health.down();
        } else if (openCircuits > 0L || "DEGRADED".equals(status)) {
            builder = Health.outOfService();
        } else {
            builder = Health.up();
        }
        return builder
                .withDetail("slo", slo)
                .withDetail("resilience", circuit)
                .build();
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
