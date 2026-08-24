package cn.lgs.orbisops.trigger.ops;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Delegates retained cancellation metadata and live transport subscriptions independently. */
@Service
public class OpsRunCancellationRegistry {
    private final OpsRunCancellationState state;
    private final OpsRunCancellationListeners listeners = new OpsRunCancellationListeners();

    public OpsRunCancellationRegistry() {
        this(OpsRunCancellationSettings.legacyConstructorDefaults());
    }

    @Autowired
    public OpsRunCancellationRegistry(OpsRunCancellationSettings settings) {
        this(new OpsRunCancellationState(settings, System::currentTimeMillis));
    }

    OpsRunCancellationRegistry(OpsRunCancellationState state) {
        this.state = state;
    }

    public void markCanceled(String runId) {
        String key = state.requireRunId(runId);
        state.markCanceled(key);
        listeners.cancel(key);
    }

    /** Subscribe under an authenticated Run; the state recheck closes registration races. */
    public CancellationRegistration onCancel(String runId, Runnable action) {
        String key = state.requireRunId(runId);
        var registration = listeners.register(key, action);
        if (state.isCanceled(key)) registration.cancel();
        return registration;
    }

    /** Retains the public handle name and its unchecked, idempotent close contract. */
    public abstract static class CancellationRegistration implements AutoCloseable {
        @Override public abstract void close();
    }

    public boolean isCanceled(String runId) {
        return Thread.currentThread().isInterrupted() || state.isCanceled(runId);
    }

    public boolean cancellationRequested(String runId) {
        return state.isCanceled(runId);
    }

    public void throwIfCanceled(String runId) {
        if (isCanceled(runId)) throw new OpsRunCanceledException(runId);
    }

    /** Compatibility alias for callers migrated before the registry API rename. */
    public void assertNotCanceled(String runId) {
        throwIfCanceled(runId);
    }

    public void markFinished(String runId) {
        state.markFinished(runId);
    }

    public void clear(String runId) {
        state.clear(runId);
    }
}
