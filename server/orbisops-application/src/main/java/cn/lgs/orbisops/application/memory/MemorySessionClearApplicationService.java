package cn.lgs.orbisops.application.memory;

import java.util.ArrayList;
import java.util.List;

/** Application use case for best-effort clearing of all session-scoped memory projections. */
public class MemorySessionClearApplicationService {

    private final HotMemoryClearPort hotMemoryClearPort;
    private final ColdMemoryStoreApplicationService coldMemoryStore;
    private final SemanticMemoryClearPort semanticMemoryClearPort;
    private final MemoryCaptureApplicationService captureService;
    private final MemorySessionClearFailurePort failurePort;

    public MemorySessionClearApplicationService(HotMemoryClearPort hotMemoryClearPort,
                                                ColdMemoryStoreApplicationService coldMemoryStore,
                                                SemanticMemoryClearPort semanticMemoryClearPort,
                                                MemoryCaptureApplicationService captureService,
                                                MemorySessionClearFailurePort failurePort) {
        this.hotMemoryClearPort = hotMemoryClearPort;
        this.coldMemoryStore = coldMemoryStore;
        this.semanticMemoryClearPort = semanticMemoryClearPort;
        this.captureService = captureService;
        this.failurePort = failurePort == null ? (operation, error) -> { } : failurePort;
    }

    public MemorySessionClearResult clear(String sessionId) {
        if (sessionId == null || sessionId.trim().isBlank()) {
            return MemorySessionClearResult.skipped();
        }
        String normalizedSessionId = sessionId.trim();
        List<String> failedOperations = new ArrayList<>();
        run("hot-clear", failedOperations, () -> requireHotPort().clear(normalizedSessionId));
        run("cold-clear", failedOperations, () -> requireColdStore().clear(normalizedSessionId));
        run("semantic-clear", failedOperations, () -> requireSemanticPort().clear(normalizedSessionId));
        run("capture-state-clear", failedOperations,
                () -> requireCaptureService().clearSessionState(normalizedSessionId));
        return new MemorySessionClearResult(true, failedOperations);
    }

    private void run(String operation, List<String> failedOperations, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            failedOperations.add(operation);
            observe(operation, error);
        }
    }

    private HotMemoryClearPort requireHotPort() {
        if (hotMemoryClearPort == null) throw new IllegalStateException("Hot memory clear port is unavailable");
        return hotMemoryClearPort;
    }

    private ColdMemoryStoreApplicationService requireColdStore() {
        if (coldMemoryStore == null) throw new IllegalStateException("Cold memory store is unavailable");
        return coldMemoryStore;
    }

    private SemanticMemoryClearPort requireSemanticPort() {
        if (semanticMemoryClearPort == null) {
            throw new IllegalStateException("Semantic memory clear port is unavailable");
        }
        return semanticMemoryClearPort;
    }

    private MemoryCaptureApplicationService requireCaptureService() {
        if (captureService == null) throw new IllegalStateException("Memory capture service is unavailable");
        return captureService;
    }

    private void observe(String operation, RuntimeException error) {
        try {
            failurePort.onFailure(operation, error);
        } catch (RuntimeException ignored) {
            // Session clear remains best-effort even if diagnostics are unavailable.
        }
    }
}
