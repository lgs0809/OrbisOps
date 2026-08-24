package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;

import java.util.List;

@FunctionalInterface
public interface MemoryExtractionPort {

    List<ColdMemoryItemSnapshot> extract(MemoryMessageView message);

    default Outcome extractDurably(MemoryMessageView message) { return new Outcome(extract(message), false, "EXTRACTED"); }

    record Outcome(List<ColdMemoryItemSnapshot> items, boolean modelPending, String kind) {
        public Outcome { items = items == null ? List.of() : List.copyOf(items); }
    }
}
