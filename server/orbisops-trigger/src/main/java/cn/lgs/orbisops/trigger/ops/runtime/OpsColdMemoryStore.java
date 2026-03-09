package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.List;

public interface OpsColdMemoryStore {

    void appendMessage(OpsMemoryMessage message);

    void saveItems(List<OpsMemoryItem> items);

    List<OpsMemoryItem> listItems(String sessionId, String userId, int limit);

    void clear(String sessionId);

}
