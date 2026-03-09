package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.ContextMemoryQueryPort;
import cn.lgs.orbisops.application.memory.ContextMemoryView;
import cn.lgs.orbisops.application.memory.MemoryMessageView;
import cn.lgs.orbisops.application.memory.SemanticMemoryQueryPort;
import cn.lgs.orbisops.trigger.ops.runtime.OpsContextMemoryService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSemanticMemoryStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/** Trigger adapters for Hot, Semantic and Context memory retrieval sources. */
@Component
public class OpsMemoryRetrievalSourceAdapter implements
        SemanticMemoryQueryPort,
        ContextMemoryQueryPort {

    private final OpsSemanticMemoryStore semanticMemoryStore;
    private final ObjectProvider<OpsContextMemoryService> contextMemoryServiceProvider;
    private final OpsMemoryRetrievalMapper mapper = new OpsMemoryRetrievalMapper();

    public OpsMemoryRetrievalSourceAdapter(
            OpsSemanticMemoryStore semanticMemoryStore,
            ObjectProvider<OpsContextMemoryService> contextMemoryServiceProvider) {
        this.semanticMemoryStore = semanticMemoryStore;
        this.contextMemoryServiceProvider = contextMemoryServiceProvider;
    }

    @Override
    public List<MemoryMessageView> search(String sessionId, String userId, String query, int limit) {
        return mapper.views(semanticMemoryStore.searchMessages(sessionId, userId, query, limit));
    }

    @Override
    public List<ContextMemoryView> listForScene(String scene, String userId, String projectId, int limit) {
        OpsContextMemoryService contextMemoryService = contextMemoryServiceProvider == null
                ? null
                : contextMemoryServiceProvider.getIfAvailable();
        return contextMemoryService == null
                ? List.of()
                : mapper.contexts(contextMemoryService.listForScene(scene, userId, projectId, limit));
    }
}
