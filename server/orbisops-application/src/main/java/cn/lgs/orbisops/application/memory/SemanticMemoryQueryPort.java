package cn.lgs.orbisops.application.memory;

import java.util.List;

@FunctionalInterface
public interface SemanticMemoryQueryPort {

    List<MemoryMessageView> search(String sessionId, String userId, String query, int limit);
}
