package cn.lgs.orbisops.application.memory;

import java.util.List;

@FunctionalInterface
public interface HotMemoryQueryPort {

    List<MemoryMessageView> recent(String sessionId, int limit);
}
