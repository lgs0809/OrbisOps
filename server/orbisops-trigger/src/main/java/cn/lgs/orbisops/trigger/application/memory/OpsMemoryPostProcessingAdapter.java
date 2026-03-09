package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.ContextMemoryWritePort;
import cn.lgs.orbisops.application.memory.HotMemoryQueryPort;
import cn.lgs.orbisops.application.memory.MemoryCompressionPort;
import cn.lgs.orbisops.application.memory.MemoryExtractionPort;
import cn.lgs.orbisops.application.memory.MemoryMessageView;
import cn.lgs.orbisops.application.memory.SemanticMemoryWritePort;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.trigger.ops.runtime.OpsContextCompressor;
import cn.lgs.orbisops.trigger.ops.runtime.OpsContextMemoryService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryExtractor;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryMessage;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSemanticMemoryStore;
import org.springframework.beans.factory.ObjectProvider;
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
