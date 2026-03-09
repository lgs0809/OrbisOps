package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.memory.OpsMemorySelection;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Compatibility entrypoint used by existing agent runtimes.
 */
@Service
public class OpsConversationMemoryService {

    private final OpsMemoryFacade memoryFacade;

    public OpsConversationMemoryService(OpsMemoryFacade memoryFacade) {
        this.memoryFacade = memoryFacade;
    }

    public String assembleContext(String sessionId, String userId, String query) {
        return memoryFacade.assembleContext(sessionId, userId, query);
    }

    public String assembleContext(String sessionId, String userId, String query, Map<String, Object> metadata) {
        return memoryFacade.assembleContext(sessionId, userId, query, metadata);
    }

    public OpsMemorySelection assembleSelection(String sessionId, String userId, String query, Map<String, Object> metadata) {
        return memoryFacade.assembleSelection(sessionId, userId, query, metadata);
    }

    public void appendMessage(String sessionId, String userId, String role, String content, Map<String, Object> metadata) {
        memoryFacade.appendMessage(sessionId, userId, role, content, metadata);
    }

    public void clear(String sessionId) {
        memoryFacade.clear(sessionId);
    }

}
