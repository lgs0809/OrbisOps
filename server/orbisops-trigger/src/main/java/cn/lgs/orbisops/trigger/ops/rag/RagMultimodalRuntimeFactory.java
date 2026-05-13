package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagMultimodalRepository;

/** Composition helper assembling repository-backed multimodal RAG runtime collaborators. */
public final class RagMultimodalRuntimeFactory {

    private RagMultimodalRuntimeFactory() {
    }

    public static RagMultimodalRuntimeComponents create(
            IRagMultimodalRepository repository,
            RagMultimodalSettings settings) {
        return create(
                repository,
                settings,
                new RagMultimodalEmbeddingProtocol(settings),
                new RagMultimodalMediaPreparer(settings));
    }

    static RagMultimodalRuntimeComponents create(
            IRagMultimodalRepository repository,
            RagMultimodalSettings settings,
            RagMultimodalEmbeddingProtocol embeddingProtocol,
            RagMultimodalMediaPreparer mediaPreparer) {
        if (repository == null) throw new IllegalArgumentException("RAG_MULTIMODAL_REPOSITORY_REQUIRED");
        if (settings == null) throw new IllegalArgumentException("RAG_MULTIMODAL_SETTINGS_REQUIRED");
        if (embeddingProtocol == null) throw new IllegalArgumentException("RAG_MULTIMODAL_EMBEDDING_PROTOCOL_REQUIRED");
        if (mediaPreparer == null) throw new IllegalArgumentException("RAG_MULTIMODAL_MEDIA_PREPARER_REQUIRED");
        RagMultimodalAvailability availability = new RagMultimodalAvailability(settings, repository);
        RagMultimodalTableReadiness tableReadiness = new RagMultimodalTableReadiness(
                new RagMultimodalTableInitializer(repository, settings));
        RagMultimodalIngestionProjector ingestionProjector = new RagMultimodalIngestionProjector(settings);
        RagMultimodalVectorStore vectorStore = new RagMultimodalVectorStore(repository, settings);
        RagMultimodalRetrievalCoordinator retrievalCoordinator = new RagMultimodalRetrievalCoordinator(
                embeddingProtocol,
                vectorStore);
        return new RagMultimodalRuntimeComponents(
                settings,
                availability,
                tableReadiness,
                embeddingProtocol,
                mediaPreparer,
                ingestionProjector,
                vectorStore,
                retrievalCoordinator);
    }
}
