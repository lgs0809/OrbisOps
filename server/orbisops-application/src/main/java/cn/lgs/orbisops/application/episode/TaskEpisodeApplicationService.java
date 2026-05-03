package cn.lgs.orbisops.application.episode;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Ordered durable classification; model work runs outside every SQL transaction. */
public final class TaskEpisodeApplicationService implements AutoCloseable {
    private final TaskEpisodeStore store;
    private final TaskEpisodeModelPort model;
    private final Clock clock;
    private final long timeoutMillis;
    private final String owner = UUID.randomUUID().toString();
    private final ThreadPoolExecutor calls = new ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), work -> { Thread thread = new Thread(work, "episode-model"); thread.setDaemon(true); return thread; },
            new ThreadPoolExecutor.AbortPolicy());

    public TaskEpisodeApplicationService(TaskEpisodeStore store, TaskEpisodeModelPort model, Clock clock) {
        this(store, model, clock, TimeUnit.SECONDS.toMillis(TaskEpisodeModelPort.CALL_TIMEOUT_SECONDS));
    }
    public TaskEpisodeApplicationService(TaskEpisodeStore store, TaskEpisodeModelPort model, Clock clock, long timeoutMillis) {
        this.store = store; this.model = model; this.clock = clock; this.timeoutMillis = timeoutMillis;
    }
    public int replay(int limit) {
        int bound = Math.max(1, Math.min(limit, 32));
        store.discover(100);
        int processed = 0;
        boolean available = model.available();
        for (String session : store.pendingSessions(clock.millis(), bound)) {
            var pending = store.claimTurn(session, owner, clock.millis(), available);
            if (pending.isEmpty()) continue;
            var claim = pending.get();
            if (!available) { store.fail(claim, "EPISODE_MODEL_UNAVAILABLE", true, clock.millis()); continue; }
            try {
                var decision = bounded(() -> model.classify(claim.inputJson()));
                if (store.assign(claim, decision, clock.millis())) processed++;
            } catch (RuntimeException error) { store.fail(claim, reason(error), false, clock.millis(), error instanceof TaskEpisodeModelPort.RetryableFailure retry ? retry.retryAfterMillis() : 0); }
        }
        for (long id : store.pendingConsolidations(clock.millis(), bound)) {
            var pending = store.claimConsolidation(id, owner, clock.millis(), available);
            if (pending.isEmpty()) continue;
            var claim = pending.get();
            if (!available) { store.fail(claim, "EPISODE_MODEL_UNAVAILABLE", true, clock.millis()); continue; }
            try {
                String content = bounded(() -> model.consolidate(claim.inputJson()));
                if (content == null || content.isBlank() || content.length() > 20000)
                    throw new IllegalArgumentException("EPISODE_ARTIFACT_INVALID");
                if (store.complete(claim, content, clock.millis())) processed++;
            } catch (RuntimeException error) { store.fail(claim, reason(error), false, clock.millis(), error instanceof TaskEpisodeModelPort.RetryableFailure retry ? retry.retryAfterMillis() : 0); }
        }
        return processed;
    }
    public int sweep(String project, String reason) { return store.sweep(project, reason, clock.millis(), 1000); }
    public Map<String, Object> view(String project, String session, String user, boolean admin, int limit) {
        return store.view(project, session, user, admin, limit);
    }
    private <T> T bounded(Supplier<T> operation) {
        Future<T> future;
        try { future = calls.submit(operation::get); }
        catch (RejectedExecutionException busy) { throw new IllegalStateException("EPISODE_MODEL_CAPACITY", busy); }
        try { return future.get(timeoutMillis, TimeUnit.MILLISECONDS); }
        catch (TimeoutException timeout) { future.cancel(true); throw new IllegalStateException("EPISODE_MODEL_TIMEOUT", timeout); }
        catch (InterruptedException interrupted) { future.cancel(true); Thread.currentThread().interrupt(); throw new IllegalStateException("EPISODE_MODEL_INTERRUPTED", interrupted); }
        catch (ExecutionException failed) {
            if (failed.getCause() instanceof TaskEpisodeModelPort.RetryableFailure retry) throw retry;
            throw new IllegalStateException("EPISODE_MODEL_FAILED", failed.getCause());
        }
    }
    private String reason(RuntimeException error) {
        // Provider responses, prompts and credentials must never enter the public error field.
        String text = error.getMessage();
        return text != null && text.matches("EPISODE_[A-Z_]{1,80}") ? text : "EPISODE_PROCESSING_FAILED";
    }
    @Override public void close() { calls.shutdownNow(); }
}
