package cn.lgs.orbisops.trigger.ops.rag;

/** Immutable collaborator set assembled for the multimodal RAG runtime service. */
public record RagMultimodalRuntimeComponents(
        RagMultimodalSettings settings,
        RagMultimodalAvailability availability,
        RagMultimodalTableReadiness tableReadiness,
        RagMultimodalEmbeddingProtocol embeddingProtocol,
        RagMultimodalMediaPreparer mediaPreparer,
        RagMultimodalIngestionProjector ingestionProjector,
        RagMultimodalVectorStore vectorStore,
        RagMultimodalRetrievalCoordinator retrievalCoordinator) {

    public RagMultimodalRuntimeComponents {
        if (settings == null) throw new IllegalArgumentException("RAG_MULTIMODAL_SETTINGS_REQUIRED");
        if (availability == null) throw new IllegalArgumentException("RAG_MULTIMODAL_AVAILABILITY_REQUIRED");
        if (tableReadiness == null) throw new IllegalArgumentException("RAG_MULTIMODAL_TABLE_READINESS_REQUIRED");
        if (embeddingProtocol == null) throw new IllegalArgumentException("RAG_MULTIMODAL_EMBEDDING_PROTOCOL_REQUIRED");
        if (mediaPreparer == null) throw new IllegalArgumentException("RAG_MULTIMODAL_MEDIA_PREPARER_REQUIRED");
        if (ingestionProjector == null) throw new IllegalArgumentException("RAG_MULTIMODAL_INGESTION_PROJECTOR_REQUIRED");
        if (vectorStore == null) throw new IllegalArgumentException("RAG_MULTIMODAL_VECTOR_STORE_REQUIRED");
        if (retrievalCoordinator == null) throw new IllegalArgumentException("RAG_MULTIMODAL_RETRIEVAL_COORDINATOR_REQUIRED");
    }
}
