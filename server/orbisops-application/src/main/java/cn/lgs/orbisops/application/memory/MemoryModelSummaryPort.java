package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;

import java.util.List;

/** Secondary port for optional model-produced context summaries. */
@FunctionalInterface
public interface MemoryModelSummaryPort {

    String summarize(List<ColdMemoryMessageSnapshot> messages, int maxInputChars);
}
