package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Primary
@ConditionalOnProperty(prefix = "orbisops.chat.memory", name = "jdbc-enabled", havingValue = "false")
public class OpsNoopColdMemoryStore implements OpsColdMemoryStore {

    @Override
    public void appendMessage(OpsMemoryMessage message) {
    }

    @Override
    public void saveItems(List<OpsMemoryItem> items) {
    }

    @Override
    public List<OpsMemoryItem> listItems(String sessionId, String userId, int limit) {
        return List.of();
    }

    @Override
    public void clear(String sessionId) {
    }

}
