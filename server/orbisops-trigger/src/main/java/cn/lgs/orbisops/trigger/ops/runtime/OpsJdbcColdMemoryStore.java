package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.memory.ColdMemoryStoreApplicationService;
import cn.lgs.orbisops.trigger.application.memory.OpsColdMemoryMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/** Legacy runtime compatibility adapter over the Cold Memory Application use case. */
@Service
@ConditionalOnProperty(prefix = "orbisops.chat.memory", name = "jdbc-enabled", havingValue = "true", matchIfMissing = true)
public class OpsJdbcColdMemoryStore implements OpsColdMemoryStore {

    private final ColdMemoryStoreApplicationService coldMemoryStore;
    private final OpsColdMemoryMapper mapper = new OpsColdMemoryMapper();

    public OpsJdbcColdMemoryStore(ColdMemoryStoreApplicationService coldMemoryStore) {
        this.coldMemoryStore = coldMemoryStore;
    }

    @Override
    public void appendMessage(OpsMemoryMessage message) {
        coldMemoryStore.appendMessage(mapper.snapshot(message));
    }

    @Override
    public void saveItems(List<OpsMemoryItem> items) {
        coldMemoryStore.saveItems(mapper.snapshots(items));
    }

    @Override
    public List<OpsMemoryItem> listItems(String sessionId, String userId, int limit) {
        return mapper.views(coldMemoryStore.listItems(sessionId, userId, limit));
    }

    @Override
    public void clear(String sessionId) {
        coldMemoryStore.clear(sessionId);
    }
}
