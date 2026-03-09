package cn.lgs.orbisops.application.rag;

import cn.lgs.orbisops.domain.agent.model.entity.RagDocument;
import cn.lgs.orbisops.domain.agent.model.entity.RagFileResource;

import java.util.List;

/** Optional multimodal persistence boundary for parsed RAG documents and original media. */
public interface RagMultimodalWriterPort {

    void write(List<RagDocument> documents, RagFileResource sourceFile);
}
