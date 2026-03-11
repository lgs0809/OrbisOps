package cn.lgs.orbisops.application.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;

import java.util.List;

/** Writes parsed RAG chunks to the configured vector store. */
public interface RagVectorWriterPort {

    void write(List<RagDocument> documents);
}
