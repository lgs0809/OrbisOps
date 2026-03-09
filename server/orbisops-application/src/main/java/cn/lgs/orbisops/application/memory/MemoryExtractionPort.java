package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;

import java.util.List;

@FunctionalInterface
public interface MemoryExtractionPort {

    List<ColdMemoryItemSnapshot> extract(MemoryMessageView message);
}
