package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpInvocationResiliencePolicy;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpRuntimeSloTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsMcpRuntimeHealthIndicatorTest {

    @Test
    void healthySloWithoutOpenCircuitMustBeUp() {
        OpsMcpRuntimeSloTelemetry telemetry = mock(OpsMcpRuntimeSloTelemetry.class);
        OpsMcpInvocationResiliencePolicy resilience = mock(OpsMcpInvocationResiliencePolicy.class);
        when(telemetry.snapshot()).thenReturn(Map.of("sloStatus", "HEALTHY"));
        when(resilience.snapshot()).thenReturn(Map.of("openCircuits", 0L));

        assertEquals(Status.UP,
                new OpsMcpRuntimeHealthIndicator(telemetry, resilience).health().getStatus());
    }

    @Test
    void openCircuitMustBeOutOfServiceAndBreachedSloMustBeDown() {
        OpsMcpRuntimeSloTelemetry telemetry = mock(OpsMcpRuntimeSloTelemetry.class);
        OpsMcpInvocationResiliencePolicy resilience = mock(OpsMcpInvocationResiliencePolicy.class);
        when(telemetry.snapshot()).thenReturn(Map.of("sloStatus", "DEGRADED"));
        when(resilience.snapshot()).thenReturn(Map.of("openCircuits", 1L));
        OpsMcpRuntimeHealthIndicator indicator =
                new OpsMcpRuntimeHealthIndicator(telemetry, resilience);

        assertEquals(Status.OUT_OF_SERVICE, indicator.health().getStatus());

        when(telemetry.snapshot()).thenReturn(Map.of("sloStatus", "BREACHED"));
        when(resilience.snapshot()).thenReturn(Map.of("openCircuits", 0L));
        assertEquals(Status.DOWN, indicator.health().getStatus());
    }
}
