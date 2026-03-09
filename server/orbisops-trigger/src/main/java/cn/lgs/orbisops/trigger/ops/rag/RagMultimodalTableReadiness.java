package cn.lgs.orbisops.trigger.ops.rag;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Coordinates one in-flight table initialization and caches successful readiness. */
public final class RagMultimodalTableReadiness {

    private final RagMultimodalTableInitializer initializer;
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicReference<CompletableFuture<Boolean>> inFlight = new AtomicReference<>();

    public RagMultimodalTableReadiness(RagMultimodalTableInitializer initializer) {
        if (initializer == null) throw new IllegalArgumentException("RAG_MULTIMODAL_TABLE_INITIALIZER_REQUIRED");
        this.initializer = initializer;
    }

    public boolean ensureReady() {
        if (ready.get()) {
            return true;
        }
        while (true) {
            CompletableFuture<Boolean> existing = inFlight.get();
            if (existing != null) {
                return existing.join();
            }
            CompletableFuture<Boolean> created = new CompletableFuture<>();
            if (!inFlight.compareAndSet(null, created)) {
                continue;
            }
            try {
                boolean initialized = initializer.initialize();
                if (initialized) {
                    ready.set(true);
                }
                created.complete(initialized);
                return initialized;
            } catch (RuntimeException | Error exception) {
                created.completeExceptionally(exception);
                throw exception;
            } finally {
                inFlight.compareAndSet(created, null);
            }
        }
    }

    public boolean ready() {
        return ready.get();
    }
}
