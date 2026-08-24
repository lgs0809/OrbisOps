package cn.lgs.orbisops.trigger.ops;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;
import org.springframework.util.StringUtils;

/** One atomic retained signal per Run; live transport subscriptions belong to Listeners. */
final class OpsRunCancellationState {
    private static final CancellationSignal ACTIVE = new CancellationSignal(null);
    private final ConcurrentMap<String, CancellationSignal> canceledRuns = new ConcurrentHashMap<>();
    private final OpsRunCancellationSettings settings;
    private final LongSupplier clock;

    OpsRunCancellationState(OpsRunCancellationSettings settings, LongSupplier clock) {
        this.settings = settings == null ? OpsRunCancellationSettings.defaults() : settings;
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    void markCanceled(String runId) {
        String key = requireRunId(runId);
        cleanupExpired();
        canceledRuns.put(key, ACTIVE);
    }

    boolean isCanceled(String runId) {
        String key = normalize(runId);
        if (key == null) return false;
        cleanupExpired();
        return canceledRuns.containsKey(key);
    }

    void markFinished(String runId) {
        String key = normalize(runId);
        if (key == null) return;
        long finishedAt = clock.getAsLong();
        canceledRuns.computeIfPresent(key, (ignored, current) -> new CancellationSignal(finishedAt));
        cleanupExpired();
    }

    void clear(String runId) {
        String key = normalize(runId);
        if (key != null) canceledRuns.remove(key);
    }

    String requireRunId(String runId) {
        String key = normalize(runId);
        if (key == null) {
            throw new IllegalArgumentException("RUN_ID_REQUIRED：取消运行时 runId 不能为空");
        }
        return key;
    }

    private String normalize(String runId) {
        return StringUtils.hasText(runId) ? runId.trim() : null;
    }

    private void cleanupExpired() {
        long now = clock.getAsLong();
        for (String runId : canceledRuns.keySet()) {
            canceledRuns.computeIfPresent(runId, (ignored, signal) ->
                    signal.finishedAt() != null && now - signal.finishedAt() > settings.retentionMillis()
                            ? null : signal);
        }
    }

    private record CancellationSignal(Long finishedAt) { }
}
