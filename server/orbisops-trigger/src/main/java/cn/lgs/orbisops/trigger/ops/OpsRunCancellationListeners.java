package cn.lgs.orbisops.trigger.ops;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Live subscriptions only; retained Run cancellation metadata and TTL belong to State. */
final class OpsRunCancellationListeners {
    private final ConcurrentMap<String, Set<Registration>> registrations = new ConcurrentHashMap<>();

    Registration register(String runId, Runnable action) {
        Registration registration = new Registration(runId, action);
        registrations.compute(runId, (ignored, current) -> {
            Set<Registration> listeners = current == null ? ConcurrentHashMap.newKeySet() : current;
            listeners.add(registration);
            return listeners;
        });
        return registration;
    }

    void cancel(String runId) {
        registrations.getOrDefault(runId, Set.of()).forEach(Registration::cancel);
    }

    final class Registration extends OpsRunCancellationRegistry.CancellationRegistration {
        private final String runId;
        private final Runnable action;
        private boolean closed;
        private boolean canceled;

        private Registration(String runId, Runnable action) {
            this.runId = runId;
            this.action = Objects.requireNonNull(action, "cancellation action");
        }

        synchronized void cancel() {
            if (closed || canceled) return;
            canceled = true;
            try { action.run(); }
            catch (RuntimeException ignored) {
                // An already-closed transport cannot prevent cancellation of other handles.
            }
        }

        @Override public void close() {
            synchronized (this) { closed = true; }
            registrations.computeIfPresent(runId, (ignored, listeners) -> {
                listeners.remove(this);
                return listeners.isEmpty() ? null : listeners;
            });
        }
    }
}
