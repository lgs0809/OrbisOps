package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagQualityRunPersistencePort;
import cn.lgs.orbisops.application.rag.RagQualityRunResult;
import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagEvalRepository;

import java.util.Map;

/** Trigger persistence adapter projecting typed RAG quality run results for the legacy repository. */
public final class OpsRagQualityRunPersistenceAdapter
        implements RagQualityRunPersistencePort<Map<String, Object>> {

    private final IRagEvalRepository repository;
    private final OpsRagQualityEvalViewMapper viewMapper;

    public OpsRagQualityRunPersistenceAdapter(
            IRagEvalRepository repository,
            OpsRagQualityEvalViewMapper viewMapper) {
        if (repository == null || viewMapper == null) {
            throw new IllegalArgumentException("RAG_QUALITY_RUN_PERSISTENCE_DEPENDENCIES_REQUIRED");
        }
        this.repository = repository;
        this.viewMapper = viewMapper;
    }

    @Override
    public void save(RagQualityRunResult<Map<String, Object>> result) {
        repository.saveRun(viewMapper.runView(result));
    }
}
