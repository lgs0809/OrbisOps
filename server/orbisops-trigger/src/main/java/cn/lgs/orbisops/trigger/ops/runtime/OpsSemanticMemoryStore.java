package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.List;

public interface OpsSemanticMemoryStore {

    void appendMessage(OpsMemoryMessage message);

    List<OpsMemoryMessage> searchMessages(String sessionId, String userId, String query, int limit);

    void clear(String sessionId);

}
