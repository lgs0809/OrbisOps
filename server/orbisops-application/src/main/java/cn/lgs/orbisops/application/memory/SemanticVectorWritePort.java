package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.SemanticMemoryDocumentSnapshot;

/** Secondary port for vector semantic-memory persistence. */
@FunctionalInterface
public interface SemanticVectorWritePort {

    void writeVector(SemanticMemoryDocumentSnapshot document);
}
