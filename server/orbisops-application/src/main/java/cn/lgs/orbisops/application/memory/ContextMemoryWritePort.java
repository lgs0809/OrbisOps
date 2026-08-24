package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;

import java.util.List;

@FunctionalInterface
public interface ContextMemoryWritePort {

    void saveExtractedItems(List<ColdMemoryItemSnapshot> items);

    default void saveExtractedItemsStrict(List<ColdMemoryItemSnapshot> items) {
        saveExtractedItems(items);
    }
}
