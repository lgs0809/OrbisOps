package cn.lgs.orbisops.application.rag;

import java.util.List;

/** Outbound online retrieval boundary for RAG quality probes. */
public interface RagQualityRetrievalPort<M> {

    List<RagQualityRetrievalHit<M>> retrieve(RagQualityProbeCommand command);
}
