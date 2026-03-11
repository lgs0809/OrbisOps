package cn.lgs.orbisops.application.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;

import java.util.List;

/** Optional multimodal persistence boundary for parsed RAG documents and original media. */
public interface RagMultimodalWriterPort {

    void write(List<RagDocument> documents, RagFileResource sourceFile);
}
