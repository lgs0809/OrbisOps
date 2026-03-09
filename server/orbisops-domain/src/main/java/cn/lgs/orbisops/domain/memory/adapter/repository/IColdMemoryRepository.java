package cn.lgs.orbisops.domain.memory.adapter.repository;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;

import java.util.List;

/** Authoritative persistence boundary for cold conversation memory. */
public interface IColdMemoryRepository {

    boolean available();

    void appendMessage(ColdMemoryMessageSnapshot message);

    void saveItems(List<ColdMemoryItemSnapshot> items);

    List<ColdMemoryItemSnapshot> listItems(String sessionId, String userId, int limit);

    void clear(String sessionId);
}
