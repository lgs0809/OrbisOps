package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.MemoryCompressionPlan;
import cn.lgs.orbisops.domain.memory.model.MemoryCompressionProjection;
import cn.lgs.orbisops.domain.memory.model.MemoryCompressionSummary;
import cn.lgs.orbisops.domain.memory.service.MemoryCompressionPolicy;
import cn.lgs.orbisops.domain.memory.service.MemoryContentHashPolicy;
import cn.lgs.orbisops.domain.memory.adapter.repository.IConversationMemoryRepository;
import cn.lgs.orbisops.domain.memory.model.ConversationMemoryWindow;

import java.util.List;
import java.util.function.Supplier;

/** Application orchestration for model/rule summarization and hot/cold compression effects. */
public class MemoryCompressionApplicationService {

    private final MemoryCompressionPolicy compressionPolicy;
    private final MemoryModelSummaryPort modelSummaryPort;
    private final HotMemoryReplacePort hotMemoryReplacePort;
    private final ColdMemoryStoreApplicationService coldMemoryStore;
    private final Supplier<String> createdAtSupplier;
    private final IConversationMemoryRepository conversationRepository;

    public MemoryCompressionApplicationService(MemoryCompressionPolicy compressionPolicy,
                                               MemoryModelSummaryPort modelSummaryPort,
                                               HotMemoryReplacePort hotMemoryReplacePort,
                                               ColdMemoryStoreApplicationService coldMemoryStore,
                                               Supplier<String> createdAtSupplier) {
        this(compressionPolicy, modelSummaryPort, null, createdAtSupplier);
    }

    public MemoryCompressionApplicationService(MemoryCompressionPolicy compressionPolicy,
                                               MemoryModelSummaryPort modelSummaryPort,
                                               IConversationMemoryRepository conversationRepository,
                                               Supplier<String> createdAtSupplier) {
        this.compressionPolicy = compressionPolicy == null
                ? new MemoryCompressionPolicy(new MemoryContentHashPolicy())
                : compressionPolicy;
        this.modelSummaryPort = modelSummaryPort;
        this.hotMemoryReplacePort = null;
        this.coldMemoryStore = null;
        this.conversationRepository = conversationRepository;
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
        if (command == null || !hasText(command.sessionId()) || conversationRepository == null) {
            return false;
        }
        ConversationMemoryWindow window = conversationRepository.window(command.sessionId(), 256, false).orElse(null);
        if (window == null) return false;
        MemoryCompressionPlan plan = compressionPolicy.plan(
                window.context(),
                command.thresholdMessages(),
                command.keepRecent());
        if (!plan.required()) {
            return false;
        }
        MemoryCompressionSummary summary = summary(plan, command);
        long coveredSeq = plan.olderMessages().stream().mapToLong(ConversationMemoryWindow::sequence).max().orElse(0);
        if (!conversationRepository.commitSummary(window, coveredSeq, summary.content(),
                compressionPolicy.protectedMessages(window.protectedMessages(), plan.olderMessages()), summary.source())) {
            throw new IllegalStateException("MEMORY_SUMMARY_REVISION_CHANGED");
        }
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
