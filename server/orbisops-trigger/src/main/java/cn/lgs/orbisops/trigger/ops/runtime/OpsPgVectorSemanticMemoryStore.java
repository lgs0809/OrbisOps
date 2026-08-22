package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.memory.SemanticMemoryApplicationFacade;
import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.trigger.application.memory.OpsSemanticMemoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** Trigger compatibility facade over the typed Semantic Memory application use case. */
@Service
@ConditionalOnProperty(
        prefix = "orbisops.chat.memory",
        name = "semantic-enabled",
        havingValue = "true")
public class OpsPgVectorSemanticMemoryStore implements OpsSemanticMemoryStore {

    private final ModelAvailabilityPort aiModelAvailability;
    private final SemanticMemoryApplicationFacade applicationFacade;
    private final OpsSemanticMemoryMapper memoryMapper = new OpsSemanticMemoryMapper();
    private final OpsSemanticMemoryRetrievalSettings settings;

    public OpsPgVectorSemanticMemoryStore(
            ModelAvailabilityPort aiModelAvailability,
            SemanticMemoryApplicationFacade applicationFacade,
            int semanticTopK,
            boolean recencyAwareEnabled,
            double recencyHalfLifeTurns) {
        this(
                aiModelAvailability,
                applicationFacade,
                new OpsSemanticMemoryRetrievalSettings(
                        semanticTopK,
                        recencyAwareEnabled,
                        recencyHalfLifeTurns));
    }

    @Autowired
    public OpsPgVectorSemanticMemoryStore(
            ModelAvailabilityPort aiModelAvailability,
            SemanticMemoryApplicationFacade applicationFacade,
            OpsSemanticMemoryRetrievalSettings settings) {
        this.aiModelAvailability = aiModelAvailability;
        this.applicationFacade = applicationFacade;
        this.settings = settings == null
                ? OpsSemanticMemoryRetrievalSettings.from(null)
                : settings;
    }

    @Override
    public void appendMessage(OpsMemoryMessage message) {
        if (message == null || !StringUtils.hasText(message.getContent())) return;
        applicationFacade.write(memoryMapper.writeCommand(
                message,
                aiModelAvailability.isEmbeddingAvailable()));
    }

    @Override
    public List<OpsMemoryMessage> searchMessages(
            String sessionId,
            String userId,
            String query,
            int limit) {
        if (!StringUtils.hasText(query)) return List.of();
        return memoryMapper.messageViews(
                applicationFacade.search(memoryMapper.retrievalQuery(
                        sessionId,
                        userId,
                        query,
                        limit,
                        settings.semanticTopK(),
                        aiModelAvailability.isEmbeddingAvailable(),
                        settings.recencyAwareEnabled(),
                        settings.recencyHalfLifeTurns())),
                sessionId,
                userId);
    }

    @Override
    public void clear(String sessionId) {
        applicationFacade.clear(sessionId);
    }
}
