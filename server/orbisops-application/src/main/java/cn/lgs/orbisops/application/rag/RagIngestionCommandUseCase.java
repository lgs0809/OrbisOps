package cn.lgs.orbisops.application.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagParsePolicy;

import java.util.List;

/** Inbound application boundary for synchronous RAG document ingestion. */
public interface RagIngestionCommandUseCase {

    void storeRagFile(String name, String tag, List<RagFileResource> files);

    default void storeRagFile(String name,
                              String tag,
                              List<RagFileResource> files,
                              RagParsePolicy parsePolicy) {
        storeRagFile(name, tag, files);
    }
}
