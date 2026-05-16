package cn.lgs.orbisops.trigger.ops;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Thread-safe cancellation signal state with bounded post-finish retention. */
final class OpsRunCancellationState {

    private final Set<String> canceledRunIds = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> canceledFinishedAt = new ConcurrentHashMap<>();
    private final OpsRunCancellationSettings settings;
    private final LongSupplier clock;

    OpsRunCancellationState(
            OpsRunCancellationSettings settings,
            LongSupplier clock) {
        this.settings = settings == null
                ? OpsRunCancellationSettings.defaults()
                : settings;
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    void markCanceled(String runId) {
        cleanupExpired();
        canceledRunIds.add(runId);
        canceledFinishedAt.remove(runId);
    }

    boolean isCanceled(String runId) {
        cleanupExpired();
        return canceledRunIds.contains(runId);
    }

    void markFinished(String runId) {
        if (canceledRunIds.contains(runId)) {
            canceledFinishedAt.put(runId, clock.getAsLong());
        } else {
            clear(runId);
        }
        cleanupExpired();
    }

    void clear(String runId) {
        canceledRunIds.remove(runId);
        canceledFinishedAt.remove(runId);
    }

    private void cleanupExpired() {
        long now = clock.getAsLong();
        canceledFinishedAt.entrySet().removeIf(entry -> {
            boolean expired = now - entry.getValue() > settings.retentionMillis();
            if (expired) {
                canceledRunIds.remove(entry.getKey());
            }
            return expired;
        });
    }
}
