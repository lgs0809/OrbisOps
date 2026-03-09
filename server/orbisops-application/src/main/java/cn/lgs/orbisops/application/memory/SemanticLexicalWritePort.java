package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.SemanticMemoryDocumentSnapshot;

/** Secondary port for PostgreSQL lexical semantic-memory persistence. */
@FunctionalInterface
public interface SemanticLexicalWritePort {

    void writeLexical(SemanticMemoryDocumentSnapshot document);
}
