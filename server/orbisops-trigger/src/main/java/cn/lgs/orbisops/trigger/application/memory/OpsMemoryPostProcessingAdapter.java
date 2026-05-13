package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.ContextMemoryWritePort;
import cn.lgs.orbisops.application.memory.HotMemoryQueryPort;
import cn.lgs.orbisops.application.memory.MemoryCompressionPort;
import cn.lgs.orbisops.application.memory.MemoryExtractionPort;
import cn.lgs.orbisops.application.memory.MemoryMessageView;
import cn.lgs.orbisops.application.memory.SemanticMemoryWritePort;
import cn.lgs.orbisops.application.memory.ContextMemoryStoreApplicationService;
import cn.lgs.orbisops.application.memory.SemanticMemoryApplicationFacade;
import cn.lgs.orbisops.application.memory.SemanticMemoryWriteCommand;
import cn.lgs.orbisops.application.memory.SemanticMemoryWriteResult;
import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.trigger.ops.runtime.OpsContextCompressor;
import cn.lgs.orbisops.trigger.ops.runtime.OpsContextMemoryService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryExtractor;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryMessage;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSemanticMemoryStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/** Trigger adapters for semantic write, extraction, context persistence and compression. */
@Component
public class OpsMemoryPostProcessingAdapter implements SemanticMemoryWritePort,
        MemoryExtractionPort,
        ContextMemoryWritePort,
        MemoryCompressionPort {

    private final OpsSemanticMemoryStore semanticMemoryStore;
    private final OpsMemoryExtractor memoryExtractor;
    private final ObjectProvider<OpsContextMemoryService> contextMemoryServiceProvider;
    private final HotMemoryQueryPort hotMemoryQueryPort;
    private final OpsContextCompressor contextCompressor;
    private final OpsMemoryRetrievalMapper messageMapper = new OpsMemoryRetrievalMapper();
    private final OpsColdMemoryMapper coldMemoryMapper = new OpsColdMemoryMapper();
    private ContextMemoryStoreApplicationService durableContextStore;
    private SemanticMemoryApplicationFacade durableSemanticStore;
    private ModelAvailabilityPort modelAvailability;
    private boolean semanticEnabled;

    public OpsMemoryPostProcessingAdapter(
            OpsSemanticMemoryStore semanticMemoryStore,
            OpsMemoryExtractor memoryExtractor,
            ObjectProvider<OpsContextMemoryService> contextMemoryServiceProvider,
            HotMemoryQueryPort hotMemoryQueryPort,
            OpsContextCompressor contextCompressor) {
        this.semanticMemoryStore = semanticMemoryStore;
        this.memoryExtractor = memoryExtractor;
        this.contextMemoryServiceProvider = contextMemoryServiceProvider;
        this.hotMemoryQueryPort = hotMemoryQueryPort;
        this.contextCompressor = contextCompressor;
    }

    @Autowired
    public void configureDurableStores(ContextMemoryStoreApplicationService contextStore,
                                       SemanticMemoryApplicationFacade semanticStore,
                                       ModelAvailabilityPort availability,
                                       @Value("${orbisops.chat.memory.semantic-enabled:false}") boolean enabled) {
        this.durableContextStore = contextStore;
        this.durableSemanticStore = semanticStore;
        this.modelAvailability = availability;
        this.semanticEnabled = enabled;
    }

    @Override
    public String appendDurably(MemoryMessageView message) {
        if (!semanticEnabled) return "DISABLED";
        if (durableSemanticStore == null) throw new IllegalStateException("SEMANTIC_MEMORY_STORE_UNAVAILABLE");
        SemanticMemoryWriteResult result = durableSemanticStore.write(new SemanticMemoryWriteCommand(
                message.sessionId(), message.userId(), message.role(), message.content(), message.createdAt(), message.metadata(),
                modelAvailability != null && modelAvailability.isEmbeddingAvailable()));
        if (!result.written()) throw new IllegalStateException("SEMANTIC_MEMORY_WRITE_FAILED");
        return result.storage().toUpperCase(java.util.Locale.ROOT);
    }

    @Override
    public void saveExtractedItemsStrict(List<ColdMemoryItemSnapshot> items) {
        if (items == null || items.isEmpty()) return;
        if (durableContextStore == null) throw new IllegalStateException("CONTEXT_MEMORY_STORE_UNAVAILABLE");
        durableContextStore.saveExtractedItemsStrict(items);
    }

    @Override
    public void append(MemoryMessageView message) {
        semanticMemoryStore.appendMessage(messageMapper.message(message));
    }

    @Override
    public List<ColdMemoryItemSnapshot> extract(MemoryMessageView message) {
        return coldMemoryMapper.snapshots(memoryExtractor.extract(messageMapper.message(message)));
    }

    @Override
    public void saveExtractedItems(List<ColdMemoryItemSnapshot> items) {
        OpsContextMemoryService contextMemoryService = contextMemoryServiceProvider == null
                ? null
                : contextMemoryServiceProvider.getIfAvailable();
        if (contextMemoryService != null) {
            contextMemoryService.saveExtractedItems(items);
        }
    }

    @Override
    public void compress(String sessionId, String userId, int bufferSize) {
        List<OpsMemoryMessage> recentMessages = hotMemoryQueryPort.recent(
                        sessionId,
                        bufferSize).stream()
                .map(messageMapper::message)
                .toList();
        contextCompressor.compressIfNeeded(
                sessionId,
                userId,
                recentMessages,
                bufferSize);
    }
}
