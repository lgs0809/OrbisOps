package cn.lgs.orbisops.application.memory;

/** Application boundary for clearing one semantic-memory session. */
public class SemanticMemoryClearApplicationService {

    private final SemanticMemoryClearPersistencePort persistencePort;
    private final SemanticMemoryClearFailurePort failurePort;

    public SemanticMemoryClearApplicationService(
            SemanticMemoryClearPersistencePort persistencePort,
            SemanticMemoryClearFailurePort failurePort) {
        this.persistencePort = persistencePort;
        this.failurePort = failurePort;
    }

    public boolean clear(String sessionId) {
        if (!hasText(sessionId) || persistencePort == null) {
            return false;
        }
        try {
            return persistencePort.clearSession(sessionId.trim());
        } catch (RuntimeException error) {
            observeFailure(error);
            return false;
        }
    }

    private void observeFailure(RuntimeException error) {
        if (failurePort == null) {
            return;
        }
        try {
            failurePort.onClearFailure(error);
        } catch (RuntimeException ignored) {
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
