package cn.lgs.orbisops.domain.memory.adapter.repository;

import cn.lgs.orbisops.domain.memory.model.ContextMemorySearchCriteria;
import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;

import java.util.List;
import java.util.Optional;

/** Persistence port for user/project context memories. */
public interface IContextMemoryRepository {

    boolean available();

    List<ContextMemorySnapshot> search(ContextMemorySearchCriteria criteria);

    Optional<ContextMemorySnapshot> findByMemoryId(String memoryId);

    boolean exists(String memoryId);

    void upsert(ContextMemorySnapshot snapshot);

    boolean updateStatus(String memoryId, String status);
}
