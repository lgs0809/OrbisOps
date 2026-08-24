package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.types.common.TraceContext;

import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded executors propagate tracing, but never implicitly inherit a foreground Run claim.
 * Model-call boundaries explicitly capture their current claim; background jobs keep independent control. */
public final class OpsTraceAwareExecutorFactory {

    public ThreadPoolExecutor create(OpsExecutorPoolSettings settings) {
        OpsExecutorPoolSettings resolved = settings == null
                ? OpsExecutorPoolSettings.resolve(
                        null, null, null, null, null,
                        "ops-worker-", 1, 1, 1, "CallerRunsPolicy")
                : settings;
        return new TraceAwareThreadPoolExecutor(
                resolved.corePoolSize(),
                resolved.maxPoolSize(),
                resolved.keepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(resolved.queueCapacity()),
                namedThreadFactory(resolved.threadNamePrefix()),
                countingRejectionHandler(rejectionHandler(resolved.rejectionPolicy())));
    }

    long rejectedCount(ThreadPoolExecutor executor) {
        if (executor == null) return 0L;
        RejectedExecutionHandler handler = executor.getRejectedExecutionHandler();
        return handler instanceof CountingRejectedExecutionHandler counting
                ? counting.rejectedCount()
                : 0L;
    }

    private RejectedExecutionHandler countingRejectionHandler(
            RejectedExecutionHandler delegate) {
        return new CountingRejectedExecutionHandler(delegate);
    }

    RejectedExecutionHandler rejectionHandler(String policy) {
        String normalized = policy == null ? "" : policy.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "abortpolicy" -> new ThreadPoolExecutor.AbortPolicy();
            case "discardpolicy" -> new ThreadPoolExecutor.DiscardPolicy();
            case "discardoldestpolicy" -> new ThreadPoolExecutor.DiscardOldestPolicy();
            default -> new ThreadPoolExecutor.CallerRunsPolicy();
        };
    }

    private ThreadFactory namedThreadFactory(String threadNamePrefix) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(threadNamePrefix + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class TraceAwareThreadPoolExecutor extends ThreadPoolExecutor {

        private TraceAwareThreadPoolExecutor(
                int corePoolSize,
                int maximumPoolSize,
                long keepAliveTime,
                TimeUnit unit,
                BlockingQueue<Runnable> workQueue,
                ThreadFactory threadFactory,
                RejectedExecutionHandler handler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        }

        @Override
        public void execute(Runnable command) {
            Runnable isolated = () -> OpsRunControlContext.withBinding(null, () -> {
                command.run(); return null;
            });
            super.execute(TraceContext.wrap(isolated));
        }

        @Override
        public Future<?> submit(Runnable task) {
            Callable<Void> wrapped = () -> OpsRunControlContext.withBinding(null, () -> {
                task.run(); return null;
            });
            return super.submit(TraceContext.wrap(wrapped));
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            Callable<T> wrapped = () -> OpsRunControlContext.withBinding(null, () -> {
                task.run(); return result;
            });
            return super.submit(TraceContext.wrap(wrapped));
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            Callable<T> wrapped = () -> OpsRunControlContext.callWithBinding(null, task);
            return super.submit(TraceContext.wrap(wrapped));
        }
    }

    private static final class CountingRejectedExecutionHandler
            implements RejectedExecutionHandler {

        private final RejectedExecutionHandler delegate;
        private final AtomicLong rejected = new AtomicLong();

        private CountingRejectedExecutionHandler(RejectedExecutionHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
            rejected.incrementAndGet();
            delegate.rejectedExecution(runnable, executor);
        }

        private long rejectedCount() {
            return rejected.get();
        }
    }
}
