package cn.lgs.orbisops.application.rag;

/** Outbound persistence boundary for one completed RAG quality evaluation run. */
public interface RagQualityRunPersistencePort<M> {

    void save(RagQualityRunResult<M> result);
}
