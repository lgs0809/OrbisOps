package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

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

    public MemoryPostProcessingApplicationService(SemanticMemoryWritePort semanticMemoryWritePort,
                                                  MemoryExtractionPort memoryExtractionPort,
                                                  ColdMemoryStoreApplicationService coldMemoryStore,
                                                  ContextMemoryWritePort contextMemoryWritePort,
                                                  MemoryCompressionPort memoryCompressionPort,
                                                  Supplier<Executor> executorSupplier,
                                                  MemoryPostProcessingFailurePort failurePort) {
        this.semanticMemoryWritePort = semanticMemoryWritePort;
        this.memoryExtractionPort = memoryExtractionPort;
        this.coldMemoryStore = coldMemoryStore;
        this.contextMemoryWritePort = contextMemoryWritePort;
        this.memoryCompressionPort = memoryCompressionPort;
        this.executorSupplier = executorSupplier == null ? () -> null : executorSupplier;
        this.failurePort = failurePort == null ? (operation, error) -> { } : failurePort;
    }

    public void submit(MemoryPostProcessingCommand command) {
        if (command == null || !command.valid()) return;
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
