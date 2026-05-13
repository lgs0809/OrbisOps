package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.model.ConversationMemoryWindow;
import cn.lgs.orbisops.domain.memory.adapter.repository.IConversationMemoryRepository;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Application orchestrator for bounded parallel retrieval from Hot, Cold, Semantic and Context memory sources.
 */
public class MemoryRetrievalApplicationService {

    private final ColdMemoryStoreApplicationService coldMemoryStore;
    private final HotMemoryQueryPort hotMemoryQueryPort;
    private final SemanticMemoryQueryPort semanticMemoryQueryPort;
    private final ContextMemoryQueryPort contextMemoryQueryPort;
    private final Supplier<Executor> executorSupplier;
    private final MemoryRetrievalFailurePort failurePort;
    private final IConversationMemoryRepository conversationRepository;

    public MemoryRetrievalApplicationService(ColdMemoryStoreApplicationService coldMemoryStore,
                                             HotMemoryQueryPort hotMemoryQueryPort,
                                             SemanticMemoryQueryPort semanticMemoryQueryPort,
                                             ContextMemoryQueryPort contextMemoryQueryPort,
                                             Supplier<Executor> executorSupplier,
                                             MemoryRetrievalFailurePort failurePort) {
        this(coldMemoryStore, hotMemoryQueryPort, semanticMemoryQueryPort, contextMemoryQueryPort,
                executorSupplier, failurePort, null);
    }

    public MemoryRetrievalApplicationService(ColdMemoryStoreApplicationService coldMemoryStore,
                                             HotMemoryQueryPort hotMemoryQueryPort,
                                             SemanticMemoryQueryPort semanticMemoryQueryPort,
                                             ContextMemoryQueryPort contextMemoryQueryPort,
                                             Supplier<Executor> executorSupplier,
                                             MemoryRetrievalFailurePort failurePort,
                                             IConversationMemoryRepository conversationRepository) {
        this.coldMemoryStore = coldMemoryStore;
        this.hotMemoryQueryPort = hotMemoryQueryPort;
        this.semanticMemoryQueryPort = semanticMemoryQueryPort;
        this.contextMemoryQueryPort = contextMemoryQueryPort;
        this.executorSupplier = executorSupplier == null ? () -> null : executorSupplier;
        this.failurePort = failurePort == null ? (slice, error) -> { } : failurePort;
        this.conversationRepository = conversationRepository;
    }

    public MemoryRetrievalResult retrieve(MemoryRetrievalQuery query) {
        if (query == null || !query.valid()) return MemoryRetrievalResult.empty();
        List<MemoryMessageView> recovered = conversationRepository == null ? null : recover(query);

        CompletableFuture<List<ColdMemoryItemSnapshot>> coldItems = load(
                "cold-items",
                () -> coldMemoryStore == null
                        ? List.of()
                        : coldMemoryStore.listItems(
                                query.sessionId(),
                                query.userId(),
                                query.coldItemLimit()),
                query.timeoutMillis());
        CompletableFuture<List<MemoryMessageView>> hotMessages = load(
                "hot-messages",
                () -> recovered != null ? recovered : hotMemoryQueryPort == null
                        ? List.of()
                        : hotMemoryQueryPort.recent(query.sessionId(), query.hotMessageLimit()),
                query.timeoutMillis());
        CompletableFuture<List<MemoryMessageView>> semanticMessages = load(
                "semantic-messages",
                () -> semanticMemoryQueryPort == null
                        ? List.of()
                        : retainedSemantic(query, semanticMemoryQueryPort.search(
                                query.sessionId(),
                                query.userId(),
                                query.query(),
                                query.semanticMessageLimit())),
                query.timeoutMillis());
        CompletableFuture<List<ContextMemoryView>> contextMemories = load(
                "context-memories",
                () -> contextMemoryQueryPort == null
                        ? List.of()
                        : contextMemoryQueryPort.listForScene(
                                query.scene(),
                                query.userId(),
                                query.projectId(),
                                query.contextMemoryLimit()),
                query.timeoutMillis());

        return new MemoryRetrievalResult(
                join("cold-items", coldItems),
                join("hot-messages", hotMessages),
                join("semantic-messages", semanticMessages),
                join("context-memories", contextMemories));
    }

    private List<MemoryMessageView> recover(MemoryRetrievalQuery query) {
        ConversationMemoryWindow window = conversationRepository.window(query.sessionId(), query.hotMessageLimit(), true).orElse(null);
        if (window == null) return List.of();
        if (!window.projectId().equals(query.projectId())
                || (window.projectId().isBlank() && !window.userId().equals(query.userId()))) {
            throw new IllegalArgumentException("MEMORY_SESSION_SCOPE_MISMATCH");
        }
        List<MemoryMessageView> result = new ArrayList<>();
        for (ColdMemoryMessageSnapshot message : window.protectedMessages()) {
            Map<String, Object> metadata = new LinkedHashMap<>(message.metadata());
            metadata.put("memory_type", "protected_source");
            result.add(new MemoryMessageView(message.sessionId(), message.userId(), message.role(), message.content(),
                    message.createdAt(), metadata));
        }
        window.context().forEach(message -> {
            Map<String, Object> metadata = new LinkedHashMap<>(message.metadata());
            if (!"summary".equals(metadata.get("memory_type"))) metadata.put("memory_type", "conversation_tail");
            result.add(new MemoryMessageView(message.sessionId(), message.userId(),
                    message.role(), message.content(), message.createdAt(), metadata));
        });
        return List.copyOf(result);
    }

    private List<MemoryMessageView> retainedSemantic(MemoryRetrievalQuery query, List<MemoryMessageView> messages) {
        if (conversationRepository == null || messages == null || messages.isEmpty()) return messages;
        List<MemoryMessageView> scoped = messages.stream()
                .filter(message -> query.sessionId().equals(message.sessionId()))
                .filter(message -> query.projectId().equals(message.metadata().get("projectId")))
                .filter(message -> sequence(message) > 0).toList();
        List<Long> retained = conversationRepository.existingSequences(query.sessionId(), query.projectId(), query.userId(),
                scoped.stream().map(this::sequence).distinct().toList());
        return scoped.stream().filter(message -> retained.contains(sequence(message))).toList();
    }

    private long sequence(MemoryMessageView message) {
        Object value = message.metadata().get("messageSeq");
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private <T> CompletableFuture<List<T>> load(String slice,
                                                 Supplier<List<T>> supplier,
                                                 long timeoutMillis) {
        Executor executor = resolveExecutor(slice);
        if (executor == null) {
            return CompletableFuture.completedFuture(loadSlice(slice, supplier));
        }
        try {
            return CompletableFuture.supplyAsync(() -> loadSlice(slice, supplier), executor)
                    .completeOnTimeout(List.of(), timeoutMillis, TimeUnit.MILLISECONDS)
                    .exceptionally(error -> {
                        observe(slice, runtime(error));
                        return List.of();
                    });
        } catch (RuntimeException error) {
            observe(slice + "-submit", error);
            return CompletableFuture.completedFuture(loadSlice(slice, supplier));
        }
    }

    private Executor resolveExecutor(String slice) {
        try {
            return executorSupplier.get();
        } catch (RuntimeException error) {
            observe(slice + "-executor", error);
            return null;
        }
    }

    private <T> List<T> loadSlice(String slice, Supplier<List<T>> supplier) {
        try {
            List<T> values = supplier.get();
            return values == null ? List.of() : List.copyOf(values);
        } catch (RuntimeException error) {
            observe(slice, error);
            return List.of();
        }
    }

    private <T> List<T> join(String slice, CompletableFuture<List<T>> future) {
        try {
            List<T> values = future.join();
            return values == null ? List.of() : values;
        } catch (RuntimeException error) {
            observe(slice + "-join", runtime(error));
            return List.of();
        }
    }

    private RuntimeException runtime(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null
                ? error.getCause()
                : error;
        return cause instanceof RuntimeException runtime
                ? runtime
                : new IllegalStateException(cause == null ? "memory retrieval failed" : cause.getMessage(), cause);
    }

    private void observe(String slice, RuntimeException error) {
        try {
            failurePort.onFailure(slice, error);
        } catch (RuntimeException ignored) {
            // Retrieval remains fail-open even if an observer is misconfigured.
        }
    }
}
