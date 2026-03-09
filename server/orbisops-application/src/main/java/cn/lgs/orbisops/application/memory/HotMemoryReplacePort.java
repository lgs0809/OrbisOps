package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;

import java.util.List;

/** Secondary port for replacing one session's hot-memory window atomically. */
@FunctionalInterface
public interface HotMemoryReplacePort {

    void replace(String sessionId, List<ColdMemoryMessageSnapshot> messages, int maxMessages);
}
