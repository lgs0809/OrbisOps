package cn.lgs.orbisops.trigger.http.sse;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Low-cardinality lifecycle telemetry for every HTTP SSE stream. */
@Component
public final class OpsSseTelemetry {

    private final MeterRegistry registry;
    private final AtomicLong activeStreams = new AtomicLong();

    public OpsSseTelemetry() {
        this((MeterRegistry) null);
    }

    @Autowired
    public OpsSseTelemetry(ObjectProvider<MeterRegistry> registryProvider) {
        this(registryProvider == null ? null : registryProvider.getIfAvailable());
    }

    OpsSseTelemetry(MeterRegistry registry) {
        this.registry = registry;
        if (registry != null) {
            Gauge.builder("ops_sse_streams_active", activeStreams, AtomicLong::get)
                    .description("Currently active operations SSE streams")
                    .register(registry);
        }
    }

    AutoCloseable opened(Supplier<OpsSseCompletionGuard.State> stateSupplier) {
        activeStreams.incrementAndGet();
        increment("opened");
        return new Session(stateSupplier, System.nanoTime());
    }

    long activeStreams() {
        return activeStreams.get();
    }

    private void increment(String outcome) {
        if (registry == null) return;
        Counter.builder("ops_sse_streams_total")
                .description("Operations SSE streams by lifecycle outcome")
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    private void duration(String outcome, long durationNanos) {
        if (registry == null) return;
        Timer.builder("ops_sse_stream_duration")
                .description("Operations SSE stream duration")
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(registry)
                .record(Math.max(0L, durationNanos), TimeUnit.NANOSECONDS);
    }

    private String outcome(OpsSseCompletionGuard.State state) {
        if (state == null) return "unknown";
        return state.name().toLowerCase(Locale.ROOT);
    }

    private final class Session implements AutoCloseable {

        private final Supplier<OpsSseCompletionGuard.State> stateSupplier;
        private final long startedNanos;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private Session(
                Supplier<OpsSseCompletionGuard.State> stateSupplier,
                long startedNanos) {
            this.stateSupplier = stateSupplier == null
                    ? () -> null
                    : stateSupplier;
            this.startedNanos = startedNanos;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            activeStreams.updateAndGet(value -> Math.max(0L, value - 1L));
            String terminalOutcome = outcome(stateSupplier.get());
            increment(terminalOutcome);
            duration(terminalOutcome, System.nanoTime() - startedNanos);
        }
    }
}
