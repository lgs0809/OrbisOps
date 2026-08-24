package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.adapter.repository.IColdMemoryRepository;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Application use case for fail-open Cold Memory persistence.
 *
 * <p>The historical runtime contract treats a disabled or unavailable durable store as a no-op.
 * Repository exceptions are contained and reported to an optional outer-layer observer.</p>
 */
public class ColdMemoryStoreApplicationService {

    private final IColdMemoryRepository repository;
    private final BooleanSupplier storeEnabled;
    private final ColdMemoryStoreFailurePort failurePort;

    public ColdMemoryStoreApplicationService(IColdMemoryRepository repository,
                                             BooleanSupplier storeEnabled,
                                             ColdMemoryStoreFailurePort failurePort) {
        this.repository = repository;
        this.storeEnabled = storeEnabled == null ? () -> true : storeEnabled;
        this.failurePort = failurePort == null ? (operation, error) -> { } : failurePort;
    }

    public boolean available() {
        if (!storeEnabled.getAsBoolean() || repository == null) return false;
        try {
            return repository.available();
        } catch (RuntimeException error) {
            observeFailure("availability", error);
            return false;
        }
    }

    public void appendMessage(ColdMemoryMessageSnapshot message) {
        if (message == null || !available()) return;
        try {
            repository.appendMessage(message);
        } catch (RuntimeException error) {
            observeFailure("append-message", error);
        }
    }

    public void saveItems(List<ColdMemoryItemSnapshot> items) {
        if (items == null || items.isEmpty() || !available()) return;
        try {
            repository.saveItems(items);
        } catch (RuntimeException error) {
            observeFailure("save-items", error);
        }
    }

    /** Durable worker entry: errors must roll back its lease-guarded completion transaction. */
    public void saveItemsStrict(List<ColdMemoryItemSnapshot> items) {
        if (items == null || items.isEmpty()) return;
        if (!available()) throw new IllegalStateException("COLD_MEMORY_STORE_UNAVAILABLE");
        repository.saveItems(items);
    }

    public List<ColdMemoryItemSnapshot> listItems(String sessionId, String userId, int limit) {
        if (!hasText(sessionId) || !available()) return List.of();
        try {
            List<ColdMemoryItemSnapshot> items = repository.listItems(
                    sessionId.trim(),
                    hasText(userId) ? userId.trim() : userId,
                    Math.max(1, limit));
            return items == null ? List.of() : List.copyOf(items);
        } catch (RuntimeException error) {
            observeFailure("list-items", error);
            return List.of();
        }
    }

    public void clear(String sessionId) {
        if (!hasText(sessionId) || !available()) return;
        try {
            repository.clear(sessionId.trim());
        } catch (RuntimeException error) {
            observeFailure("clear", error);
        }
    }

    private void observeFailure(String operation, RuntimeException error) {
        try {
            failurePort.onFailure(operation, error);
        } catch (RuntimeException ignored) {
            // Durable memory remains fail-open even if an observer is misconfigured.
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
