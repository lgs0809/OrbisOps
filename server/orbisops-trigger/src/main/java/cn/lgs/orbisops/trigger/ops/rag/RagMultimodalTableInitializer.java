package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagMultimodalRepository;

/** Concrete table initialization collaborator for the multimodal repository. */
public final class RagMultimodalTableInitializer {

    private final IRagMultimodalRepository repository;
    private final RagMultimodalSettings settings;

    public RagMultimodalTableInitializer(
            IRagMultimodalRepository repository,
            RagMultimodalSettings settings) {
        if (repository == null) throw new IllegalArgumentException("RAG_MULTIMODAL_REPOSITORY_REQUIRED");
        if (settings == null) throw new IllegalArgumentException("RAG_MULTIMODAL_SETTINGS_REQUIRED");
        this.repository = repository;
        this.settings = settings;
    }

    public boolean initialize() {
        return repository.ensureTable(settings.tableName(), settings.dimension());
    }
}
