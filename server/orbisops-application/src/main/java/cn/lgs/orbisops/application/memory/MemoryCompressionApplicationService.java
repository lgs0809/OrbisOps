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
        var prepared=prepareCompressionInternal(command);
        if(prepared.isEmpty())return false;
        prepared.get().run();return true;
    }

    /** Network work is prepared before the job completion transaction acquires its lease fence. */
    public Runnable prepareCompression(MemoryCompressionCommand command) {
        return prepareCompressionInternal(command).orElse(()->{});
    }

    private java.util.Optional<Runnable> prepareCompressionInternal(MemoryCompressionCommand command) {
        if(command==null || !hasText(command.sessionId()) || conversationRepository==null)return java.util.Optional.empty();
        ConversationMemoryWindow window=conversationRepository.window(command.sessionId(),256,false).orElse(null);
        if(window==null)return java.util.Optional.empty();
        MemoryCompressionPlan plan=compressionPolicy.plan(window.context(),command.thresholdMessages(),command.keepRecent());
        if(!plan.required())return java.util.Optional.empty();
        MemoryCompressionSummary summary=summary(plan,command);
        long coveredSeq=plan.olderMessages().stream().mapToLong(ConversationMemoryWindow::sequence).max().orElse(0);
        var protectedSources=compressionPolicy.protectedMessages(window.protectedMessages(),plan.olderMessages());
        String modelInput;
        if(summary.modelPending()) {
            var input=new cn.lgs.orbisops.domain.memory.model.MemorySummaryModelInput(window.sessionId(),window.projectId(),window.userId(),
                    coveredSeq,window.summaryRevision()+1,command.modelMaxInputChars(),plan.olderMessages());
            modelInput=cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(input);
        } else modelInput="";
        return java.util.Optional.of(()->{
            boolean committed=summary.modelPending()
                    ? conversationRepository.commitPendingModelSummary(window,coveredSeq,summary.content(),protectedSources,summary.source(),modelInput)
                    : conversationRepository.commitSummary(window,coveredSeq,summary.content(),protectedSources,summary.source());
            if(!committed)throw new IllegalStateException("MEMORY_SUMMARY_REVISION_CHANGED");
        });
    }

    public Runnable prepareModelRetry(cn.lgs.orbisops.domain.memory.model.MemoryProcessingJob job, boolean modelEnabled) {
        if (!modelEnabled || modelSummaryPort == null) throw new IllegalStateException("MEMORY_SUMMARY_MODEL_DISABLED");
        var input = cn.lgs.orbisops.domain.memory.model.MemorySummaryModelInput.parse(job.modelInputJson());
        if (input == null || !job.sessionId().equals(input.sessionId())
                || !java.util.Objects.equals(job.message().metadata().get("projectId"), input.projectId()))
            throw new cn.lgs.orbisops.domain.memory.model.MemoryModelInputInvalidException("MEMORY_MODEL_SUMMARY_SCOPE_INVALID");
        var current = conversationRepository.window(input.sessionId(), 2, false).orElse(null);
        if (current != null && !current.userId().equals(input.userId())) throw new cn.lgs.orbisops.domain.memory.model.MemoryModelInputInvalidException("MEMORY_MODEL_SUMMARY_SCOPE_INVALID");
        if (current == null || current.summaryRevision()!=input.summaryRevision() || current.coveredSeq()!=input.coveredSeq())
            return () -> { }; // Cleared/superseded source: no model call and no projection resurrection.
        String content = modelSummaryPort.summarize(input.messages(), input.maxInputChars());
        if (!hasText(content)) throw new IllegalStateException("MEMORY_SUMMARY_MODEL_EMPTY");
        return () -> {
            if (!conversationRepository.replacePendingModelSummary(input, content))
                throw new IllegalStateException("MEMORY_SUMMARY_REVISION_CHANGED");
        };
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
            var fallback = compressionPolicy.ruleSummary(plan.olderMessages());
            return new MemoryCompressionSummary(fallback.content(), "context_compressor_model_pending", true);
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
