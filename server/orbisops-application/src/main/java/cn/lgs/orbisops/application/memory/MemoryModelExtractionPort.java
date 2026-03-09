package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryExtractionDraft;

import java.util.List;

/** Secondary port for optional model-produced long-term memory drafts. */
@FunctionalInterface
public interface MemoryModelExtractionPort {

    List<MemoryExtractionDraft> extract(ColdMemoryMessageSnapshot message, int maxInputChars);
}
