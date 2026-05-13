package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryProcessingJob;
import cn.lgs.orbisops.domain.memory.adapter.repository.IConversationMemoryRepository;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.UUID;
import java.time.Clock;

/**
 * Application orchestrator for semantic indexing, durable extraction and conversation compression.
 */
public class MemoryPostProcessingApplicationService {

    private final SemanticMemoryWritePort semanticMemoryWritePort;
    private final MemoryExtractionPort memoryExtractionPort;
    private final ColdMemoryStoreApplicationService coldMemoryStore;
    private final ContextMemoryWritePort contextMemoryWritePort;
    private final MemoryCompressionPort memoryCompressionPort;
    private final Supplier<Executor> executorSupplier;
    private final MemoryPostProcessingFailurePort failurePort;
    private final IConversationMemoryRepository conversationRepository;
    private final Clock clock;

    public MemoryPostProcessingApplicationService(SemanticMemoryWritePort semanticMemoryWritePort,
                                                  MemoryExtractionPort memoryExtractionPort,
                                                  ColdMemoryStoreApplicationService coldMemoryStore,
                                                  ContextMemoryWritePort contextMemoryWritePort,
                                                  MemoryCompressionPort memoryCompressionPort,
                                                  Supplier<Executor> executorSupplier,
                                                  MemoryPostProcessingFailurePort failurePort) {
        this(semanticMemoryWritePort, memoryExtractionPort, coldMemoryStore, contextMemoryWritePort,
                memoryCompressionPort, executorSupplier, failurePort, null, Clock.systemUTC());
    }

    public MemoryPostProcessingApplicationService(SemanticMemoryWritePort semanticMemoryWritePort,
                                                  MemoryExtractionPort memoryExtractionPort,
                                                  ColdMemoryStoreApplicationService coldMemoryStore,
                                                  ContextMemoryWritePort contextMemoryWritePort,
                                                  MemoryCompressionPort memoryCompressionPort,
                                                  Supplier<Executor> executorSupplier,
                                                  MemoryPostProcessingFailurePort failurePort,
                                                  IConversationMemoryRepository conversationRepository, Clock clock) {
        this.semanticMemoryWritePort = semanticMemoryWritePort;
        this.memoryExtractionPort = memoryExtractionPort;
        this.coldMemoryStore = coldMemoryStore;
        this.contextMemoryWritePort = contextMemoryWritePort;
        this.memoryCompressionPort = memoryCompressionPort;
        this.executorSupplier = executorSupplier == null ? () -> null : executorSupplier;
        this.failurePort = failurePort == null ? (operation, error) -> { } : failurePort;
        this.conversationRepository = conversationRepository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public void submit(MemoryPostProcessingCommand command) {
        if (command == null || !command.valid()) return;
        if (conversationRepository != null) {
            dispatchPending(command.message().sessionId(), 12);
            return;
        }
        Runnable task = () -> process(command);
        Executor executor = resolveExecutor("post-processing-executor");
        if (executor == null) {
            observe("post-processing-submit",
                    new IllegalStateException("MEMORY_BACKGROUND_EXECUTOR_UNAVAILABLE"));
            return;
        }
        try {
            executor.execute(task);
        } catch (RuntimeException error) {
            observe("post-processing-submit", error);
        }
    }

    /** Reuse the memory executor; the database outbox survives unavailable/rejected dispatch and process exit. */
    public void replayPending(int limit) {
        if (conversationRepository != null) dispatchPending("", limit);
    }

    private void dispatchPending(String sessionId, int limit) {
        Executor executor = resolveExecutor("post-processing-executor");
        if (executor == null) return;
        for (Long id : conversationRepository.pending(sessionId, limit, clock.millis())) {
            try {
                executor.execute(() -> conversationRepository.claim(id, UUID.randomUUID().toString(), clock.millis(), 300_000L)
                        .ifPresent(this::processDurable));
            } catch (RuntimeException error) {
                observe("post-processing-submit", error);
                return;
            }
        }
    }

    private void processDurable(MemoryProcessingJob job) {
        var source = job.message();
        MemoryMessageView message = new MemoryMessageView(source.sessionId(), source.userId(), source.role(), source.content(),
                source.createdAt(), source.metadata());
        try {
            switch (job.taskType()) {
                case "SEMANTIC" -> {
                    String outcome = semanticMemoryWritePort == null ? "DISABLED" : semanticMemoryWritePort.appendDurably(message);
                    conversationRepository.complete(job, outcome, clock.millis(), null);
                }
                case "EXTRACTION" -> {
                    List<ColdMemoryItemSnapshot> items = memoryExtractionPort == null ? List.of() : memoryExtractionPort.extract(message);
                    conversationRepository.complete(job, items == null || items.isEmpty() ? "NO_FACTS" : "EXTRACTED", clock.millis(), () -> {
                        if (coldMemoryStore != null) coldMemoryStore.saveItemsStrict(items);
                        if (contextMemoryWritePort != null) contextMemoryWritePort.saveExtractedItemsStrict(items);
                    });
                }
                case "COMPRESSION" -> {
                    if (memoryCompressionPort != null) memoryCompressionPort.compress(job.sessionId(), source.userId(), job.bufferSize());
                    conversationRepository.complete(job, "CHECKED", clock.millis(), null);
                }
                default -> throw new IllegalArgumentException("MEMORY_POST_PROCESSING_TASK_TYPE_INVALID");
            }
        } catch (RuntimeException error) {
            // Store a safe diagnostic category, never provider bodies/credentials or message content.
            conversationRepository.retry(job, error.getClass().getSimpleName(), clock.millis());
            observe("durable-" + job.taskType(), error);
        }
    }

    private void process(MemoryPostProcessingCommand command) {
        appendSemantic(command.message());
        submitExtraction(command);
        compress(command);
    }

    private void appendSemantic(MemoryMessageView message) {
        if (semanticMemoryWritePort == null) return;
        try {
            semanticMemoryWritePort.append(message);
        } catch (RuntimeException error) {
            observe("semantic-append", error);
        }
    }

    private void submitExtraction(MemoryPostProcessingCommand command) {
        Runnable extractionTask = () -> extractAndPersist(command.message());
        if (!command.extractionAsyncEnabled()) {
            extractionTask.run();
            return;
        }
        Executor executor = resolveExecutor("extraction-executor");
        if (executor == null) {
            extractionTask.run();
            return;
        }
        try {
            executor.execute(extractionTask);
        } catch (RuntimeException error) {
            observe("extraction-submit", error);
            extractionTask.run();
        }
    }

    private void extractAndPersist(MemoryMessageView message) {
        if (memoryExtractionPort == null) return;
        List<ColdMemoryItemSnapshot> items;
        try {
            List<ColdMemoryItemSnapshot> extracted = memoryExtractionPort.extract(message);
            items = extracted == null ? List.of() : List.copyOf(extracted);
        } catch (RuntimeException error) {
            observe("extract", error);
            return;
        }
        if (items.isEmpty()) return;
        if (coldMemoryStore != null) {
            coldMemoryStore.saveItems(items);
        }
        if (contextMemoryWritePort == null) return;
        try {
            contextMemoryWritePort.saveExtractedItems(items);
        } catch (RuntimeException error) {
            observe("context-memory-save", error);
        }
    }

    private void compress(MemoryPostProcessingCommand command) {
        if (memoryCompressionPort == null) return;
        try {
            memoryCompressionPort.compress(
                    command.message().sessionId(),
                    command.message().userId(),
                    command.bufferSize());
        } catch (RuntimeException error) {
            observe("compress", error);
        }
    }

    private Executor resolveExecutor(String operation) {
        try {
            return executorSupplier.get();
        } catch (RuntimeException error) {
            observe(operation, error);
            return null;
        }
    }

    private void observe(String operation, RuntimeException error) {
        try {
            failurePort.onFailure(operation, error);
        } catch (RuntimeException ignored) {
            // Post-processing remains fail-open even if diagnostics are unavailable.
        }
    }
}
