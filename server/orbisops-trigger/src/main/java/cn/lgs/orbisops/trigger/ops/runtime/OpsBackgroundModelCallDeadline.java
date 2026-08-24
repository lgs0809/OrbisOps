package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsLlmTraceContext;
import cn.lgs.orbisops.trigger.ops.OpsNodeDeadlineContext;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** Bounds total background waiting, including response-body decoding and layered evidence calls. */
public final class OpsBackgroundModelCallDeadline {
    public static final int MAX_SECONDS = 60;

    private OpsBackgroundModelCallDeadline() { }

    public static <T> T call(int seconds, Supplier<T> invocation) {
        long budget = OpsNodeDeadlineContext.remainingMillis(
                TimeUnit.SECONDS.toMillis(Math.max(1, Math.min(MAX_SECONDS, seconds))));
        if (budget <= 0) throw new DeadlineExceeded(new TimeoutException());
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budget);
        var trace = OpsLlmTraceContext.current();
        var task = new FutureTask<T>(() -> OpsNodeDeadlineContext.withDeadline(deadline,
                () -> OpsLlmTraceContext.withTrace(trace, invocation)));
        // Background calls never occupy a foreground model execution slot. Interruptible HTTP
        // transport receives the same deadline; cancellation cannot declare remote execution stopped.
        var worker = new Thread(task, "ops-background-model");
        worker.setDaemon(true);
        worker.start();
        try {
            long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remaining <= 0) throw new TimeoutException();
            return task.get(remaining, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            task.cancel(true);
            throw new DeadlineExceeded(timeout);
        } catch (InterruptedException interrupted) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("BACKGROUND_MODEL_CALL_INTERRUPTED", interrupted);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new IllegalStateException("BACKGROUND_MODEL_CALL_FAILED", failure.getCause());
        }
    }

    public static final class DeadlineExceeded extends IllegalStateException {
        private DeadlineExceeded(TimeoutException cause) { super("BACKGROUND_MODEL_CALL_TIMEOUT", cause); }
    }
}
