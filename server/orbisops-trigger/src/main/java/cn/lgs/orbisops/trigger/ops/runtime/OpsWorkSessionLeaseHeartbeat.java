package cn.lgs.orbisops.trigger.ops.runtime;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps one live Work Session claim leased while the engine is blocked in long-running work
 * such as an LLM call. Runtime events are useful checkpoints, but they are not a liveness clock.
 */
final class OpsWorkSessionLeaseHeartbeat {

    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(20);
    private static final int DEFAULT_SCHEDULER_THREADS = 2;
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    private final OpsWorkSessionRunAdapter runService;
    private final Duration interval;
    private final ScheduledExecutorService scheduler;

    OpsWorkSessionLeaseHeartbeat(OpsWorkSessionRunAdapter runService) {
        this(runService, DEFAULT_INTERVAL, Executors.newScheduledThreadPool(DEFAULT_SCHEDULER_THREADS, runnable -> {
            Thread thread = new Thread(runnable,
                    "ops-work-session-heartbeat-" + THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }));
    }

    OpsWorkSessionLeaseHeartbeat(
            OpsWorkSessionRunAdapter runService,
            Duration interval,
            ScheduledExecutorService scheduler) {
        this.runService = Objects.requireNonNull(runService, "runService");
        this.interval = interval == null || interval.isZero() || interval.isNegative()
                ? DEFAULT_INTERVAL
                : interval;
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    Scope start(OpsAgentChatRequest request) {
        Objects.requireNonNull(request, "request");
        OpsAgentChatRequest leaseRequest = leaseRequest(request);
        Thread owner = Thread.currentThread();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        AtomicBoolean intentionallySuspended = new AtomicBoolean(false);

        // Prove ownership before dispatching potentially long-running model/tool work. The heartbeat
        // carries an immutable copy of the durable claim so later request metadata projection cannot
        // silently disable lease renewal while the engine is blocked in a model/tool call.
        runService.heartbeat(leaseRequest);
        long periodMillis = Math.max(1L, interval.toMillis());
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            if (failure.get() != null) return;
            try {
                runService.heartbeat(leaseRequest);
            } catch (Throwable error) {
                if (intentionallySuspended.get()) return;
                RuntimeException heartbeatFailure = error instanceof RuntimeException runtimeException
                        ? runtimeException
                        : new IllegalStateException("WORK_SESSION_HEARTBEAT_FAILED", error);
                if (failure.compareAndSet(null, heartbeatFailure)) {
                    // Best effort: unblock interruptible model/tool waits. The authoritative fence is
                    // still the lost lease; interrupt is not used to transfer ownership.
                    owner.interrupt();
                }
            }
        }, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
        return new Scope(future, failure, intentionallySuspended, runService, leaseRequest);
    }

    private OpsAgentChatRequest leaseRequest(OpsAgentChatRequest request) {
        Map<String, Object> metadata = request.getMetadata() == null
                ? Map.of()
                : new LinkedHashMap<>(request.getMetadata());
        if (text(metadata.get(OpsWorkSessionClaimMetadata.ATTEMPT_ID)).isBlank()
                || text(metadata.get(OpsWorkSessionClaimMetadata.LEASE_TOKEN)).isBlank()) {
            throw new IllegalStateException("WORK_SESSION_LEASE_CLAIM_REQUIRED");
        }
        return OpsAgentChatRequest.builder()
                .runId(request.getRunId())
                .projectId(request.getProjectId())
                .metadata(metadata)
                .build();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    static final class Scope implements AutoCloseable {
        private final ScheduledFuture<?> future;
        private final AtomicReference<RuntimeException> failure;
        private final AtomicBoolean intentionallySuspended;
        private final OpsWorkSessionRunAdapter runService;
        private final OpsAgentChatRequest leaseRequest;

        private Scope(ScheduledFuture<?> future,
                      AtomicReference<RuntimeException> failure,
                      AtomicBoolean intentionallySuspended,
                      OpsWorkSessionRunAdapter runService,
                      OpsAgentChatRequest leaseRequest) {
            this.future = future;
            this.failure = failure;
            this.intentionallySuspended = intentionallySuspended;
            this.runService = runService;
            this.leaseRequest = leaseRequest;
        }

        void suspendForApproval() {
            intentionallySuspended.set(true);
            future.cancel(false);
            runService.suspendForApproval(leaseRequest);
        }

        void assertHealthy() {
            if (intentionallySuspended.get()) return;
            RuntimeException error = failure.get();
            if (error != null) throw error;
        }

        @Override
        public void close() {
            future.cancel(false);
        }
    }
}
