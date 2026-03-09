package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.MemoryCompressionPlan;
import cn.lgs.orbisops.domain.memory.model.MemoryCompressionProjection;
import cn.lgs.orbisops.domain.memory.model.MemoryCompressionSummary;
import cn.lgs.orbisops.domain.memory.service.MemoryCompressionPolicy;
import cn.lgs.orbisops.domain.memory.service.MemoryContentHashPolicy;

import java.util.List;
import java.util.function.Supplier;

/** Application orchestration for model/rule summarization and hot/cold compression effects. */
public class MemoryCompressionApplicationService {

    private final MemoryCompressionPolicy compressionPolicy;
    private final MemoryModelSummaryPort modelSummaryPort;
    private final HotMemoryReplacePort hotMemoryReplacePort;
    private final ColdMemoryStoreApplicationService coldMemoryStore;
    private final Supplier<String> createdAtSupplier;

    public MemoryCompressionApplicationService(MemoryCompressionPolicy compressionPolicy,
                                               MemoryModelSummaryPort modelSummaryPort,
                                               HotMemoryReplacePort hotMemoryReplacePort,
                                               ColdMemoryStoreApplicationService coldMemoryStore,
                                               Supplier<String> createdAtSupplier) {
        this.compressionPolicy = compressionPolicy == null
                ? new MemoryCompressionPolicy(new MemoryContentHashPolicy())
                : compressionPolicy;
        this.modelSummaryPort = modelSummaryPort;
        this.hotMemoryReplacePort = hotMemoryReplacePort;
        this.coldMemoryStore = coldMemoryStore;
        this.createdAtSupplier = createdAtSupplier == null ? () -> "" : createdAtSupplier;
    }

    public static MemoryCompressionApplicationService rulesOnly(Supplier<String> createdAtSupplier) {
        return new MemoryCompressionApplicationService(
                new MemoryCompressionPolicy(new MemoryContentHashPolicy()),
                null,
                null,
                null,
                createdAtSupplier);
    }

    public boolean compress(MemoryCompressionCommand command) {
        if (command == null || !hasText(command.sessionId()) || command.messages().isEmpty()) {
            return false;
        }
        MemoryCompressionPlan plan = compressionPolicy.plan(
                command.messages(),
                command.thresholdMessages(),
                command.keepRecent());
        if (!plan.required()) {
            return false;
        }
        MemoryCompressionSummary summary = summary(plan, command);
        MemoryCompressionProjection projection = compressionPolicy.project(
                command.sessionId(),
                command.userId(),
                plan,
                summary,
                value(createdAtSupplier.get()));
        if (projection.summaryItem() == null
                || projection.replacementMessages().isEmpty()
                || hotMemoryReplacePort == null
                || coldMemoryStore == null) {
            return false;
        }
        hotMemoryReplacePort.replace(
                command.sessionId(),
                projection.replacementMessages(),
                command.hotBufferMessages());
        coldMemoryStore.saveItems(List.of(projection.summaryItem()));
        return true;
    }

    public String trimContext(String context, int maxChars) {
        return compressionPolicy.trimContext(context, maxChars);
    }

    private MemoryCompressionSummary summary(MemoryCompressionPlan plan,
                                             MemoryCompressionCommand command) {
        if (command.modelEnabled() && modelSummaryPort != null) {
            try {
                String modelSummary = modelSummaryPort.summarize(
                        plan.olderMessages(),
                        command.modelMaxInputChars());
                if (hasText(modelSummary)) {
                    return new MemoryCompressionSummary(modelSummary, "llm_context_compressor");
                }
            } catch (RuntimeException ignored) {
            }
        }
        return compressionPolicy.ruleSummary(plan.olderMessages());
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
