package cn.lgs.orbisops.trigger.ops;

import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/** Trusted server-created Run claim and cooperative cancellation, propagated across existing executors. */
public final class OpsRunControlContext {
    private static final ThreadLocal<Binding> CURRENT = new ThreadLocal<>();
    private OpsRunControlContext() { }

    public static Binding current() { return CURRENT.get(); }

    public static Scope open(Binding binding) {
        Binding previous = CURRENT.get();
        if (binding == null) CURRENT.remove(); else CURRENT.set(binding);
        return new Scope(previous);
    }
    public static final class Scope implements AutoCloseable {
        private final Binding previous;
        private Scope(Binding previous) { this.previous = previous; }
        @Override public void close() {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    public static <T> T callWithBinding(Binding binding, java.util.concurrent.Callable<T> work) throws Exception {
        try (Scope ignored = open(binding)) { return work.call(); }
    }

    public static <T> T withBinding(Binding binding, Supplier<T> work) {
        try (Scope ignored = open(binding)) { return work.get(); }
    }

    public record Binding(String runId, String projectId, Map<String, Object> checkpointRequest,
                          OpsRunCancellationRegistry registry) {
        public Binding {
            if (runId == null || runId.isBlank() || projectId == null || projectId.isBlank() || registry == null)
                throw new IllegalArgumentException("TRUSTED_RUN_CONTROL_REQUIRED");
            checkpointRequest = Map.copyOf(checkpointRequest);
        }
        public void assertNotCanceled() {
            if (registry.cancellationRequested(runId)) throw new OpsRunCanceledException(runId);
        }
        public AutoCloseable cancelWhileAwaiting(Future<?> future) {
            // Future.cancel wakes get() itself and interrupts only its actual worker.
            // An additional owner interrupt can arrive after the waiting scope has closed.
            return registry.onCancel(runId, () -> future.cancel(true));
        }
        public Mono<Void> cancellation() {
            return Mono.create(sink -> {
                var registration = registry.onCancel(runId,
                        sink::success);
                sink.onDispose(registration::close);
            });
        }
    }
}
