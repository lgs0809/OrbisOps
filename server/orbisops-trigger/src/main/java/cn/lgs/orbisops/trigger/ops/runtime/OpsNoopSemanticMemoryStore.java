package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Primary
@ConditionalOnProperty(prefix = "orbisops.chat.memory", name = "semantic-enabled", havingValue = "false", matchIfMissing = true)
public class OpsNoopSemanticMemoryStore implements OpsSemanticMemoryStore {

    @Override
    public void appendMessage(OpsMemoryMessage message) {
    }

    @Override
    public List<OpsMemoryMessage> searchMessages(String sessionId, String userId, String query, int limit) {
        return List.of();
    }

    @Override
    public void clear(String sessionId) {
    }

}
