package cn.lgs.orbisops.trigger.ops;

import java.util.concurrent.*;

/** One cancellation-aware JDK waiting boundary; callers retain their established queue/execution budgets. */
public final class OpsModelCallAwaiter {
    private OpsModelCallAwaiter() { }

    public static <T> reactor.core.publisher.Flux<T> cancellable(reactor.core.publisher.Flux<T> source) {
        var binding = OpsRunControlContext.current();
        if (binding == null) return source;
        // takeUntilOther cancels its source on the companion's normal signal. An error
        // companion can terminate the subscriber without disposing the source producer.
        return source.takeUntilOther(binding.cancellation()).concatWith(reactor.core.publisher.Flux.defer(() -> {
            binding.assertNotCanceled();
            return reactor.core.publisher.Flux.empty();
        }));
    }

    public static boolean started(CountDownLatch started, Future<?> future, long milliseconds)
            throws InterruptedException {
        OpsRunControlContext.Binding binding = OpsRunControlContext.current();
        var listener=binding==null?null:binding.registry().onCancel(binding.runId(),()->{
            try {future.cancel(true);} finally {started.countDown();}
        });
        try (Registration ignored = new Registration(listener)) {
            check(binding);
            boolean ready=started.await(milliseconds, TimeUnit.MILLISECONDS);
            check(binding);
            return ready;
        } catch (InterruptedException error) {
            preserveUnrelatedInterrupt(binding);
            check(binding);
            throw error;
        }
    }

    public static <T> T get(Future<T> future, long milliseconds)
            throws InterruptedException, ExecutionException, TimeoutException {
        OpsRunControlContext.Binding binding = OpsRunControlContext.current();
        try (Registration ignored = registration(binding, future)) {
            check(binding);
            T result = future.get(milliseconds, TimeUnit.MILLISECONDS);
            check(binding);
            return result;
        } catch (InterruptedException | CancellationException error) {
            // Arbitrary Future cancellation/interruption is not proof of a human Run cancellation.
            if(error instanceof InterruptedException) preserveUnrelatedInterrupt(binding);
            check(binding);
            throw error;
        }
    }

    private static void check(OpsRunControlContext.Binding binding) {
        if (binding != null && binding.registry().cancellationRequested(binding.runId())) {
            throw new OpsRunCanceledException(binding.runId());
        }
    }
    private static void preserveUnrelatedInterrupt(OpsRunControlContext.Binding binding) {
        // Trusted cancellation no longer interrupts the owner. If another component did,
        // retain that signal when a simultaneous Run cancellation changes the exception type.
        if(binding!=null && binding.registry().cancellationRequested(binding.runId())) Thread.currentThread().interrupt();
    }
    private static Registration registration(OpsRunControlContext.Binding binding, Future<?> future) {
        return new Registration(binding == null ? null : binding.cancelWhileAwaiting(future));
    }
    private record Registration(AutoCloseable listener) implements AutoCloseable {
        @Override public void close() {
            if (listener == null) return;
            try { listener.close(); }
            catch (Exception error) { throw new IllegalStateException("MODEL_CANCELLATION_UNREGISTER_FAILED", error); }
        }
    }
}
