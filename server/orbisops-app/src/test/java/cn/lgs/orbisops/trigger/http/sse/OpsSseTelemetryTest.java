package cn.lgs.orbisops.trigger.http.sse;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class OpsSseTelemetryTest {

    @Test
    void activeGaugeAndTerminalCounterCloseExactlyOnce() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OpsSseTelemetry telemetry = new OpsSseTelemetry(registry);
        SseEmitter emitter = mock(SseEmitter.class);
        OpsSseExecutionTemplate template = new OpsSseExecutionTemplate(
                new OpsSseEventWriter(), ignored -> emitter, telemetry);
        OpsSseStreamSession session = template.open(
                null, 1_000L, OpsSseExecutionTemplate.Lifecycle.defaults());

        assertEquals(1D, registry.get("ops_sse_streams_active").gauge().value());
        session.timeout(null, false);
        session.emitterCompleted();

        assertEquals(0D, registry.get("ops_sse_streams_active").gauge().value());
        assertEquals(1D, registry.get("ops_sse_streams_total")
                .tag("outcome", "opened").counter().count());
        assertEquals(1D, registry.get("ops_sse_streams_total")
                .tag("outcome", "timed_out").counter().count());
        assertEquals(1L, registry.get("ops_sse_stream_duration")
                .tag("outcome", "timed_out").timer().count());
    }

    @Test
    void lifecycleResourceReportsTheAuthoritativeTerminalState() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OpsSseTelemetry telemetry = new OpsSseTelemetry(registry);
        AtomicReference<OpsSseCompletionGuard.State> state =
                new AtomicReference<>(OpsSseCompletionGuard.State.OPEN);
        AutoCloseable session = telemetry.opened(state::get);

        state.set(OpsSseCompletionGuard.State.REJECTED);
        session.close();
        session.close();

        assertEquals(0L, telemetry.activeStreams());
        assertEquals(1D, registry.get("ops_sse_streams_total")
                .tag("outcome", "rejected").counter().count());
    }
}
