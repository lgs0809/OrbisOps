package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import org.springframework.ai.document.Document;

import java.util.List;

/** Coordinates query embedding, vector retrieval and Spring AI result projection. */
public final class RagMultimodalRetrievalCoordinator {

    private final RagMultimodalEmbeddingProtocol embeddingProtocol;
    private final RagMultimodalVectorStore vectorStore;

    public RagMultimodalRetrievalCoordinator(
            RagMultimodalEmbeddingProtocol embeddingProtocol,
            RagMultimodalVectorStore vectorStore) {
        if (embeddingProtocol == null) {
            throw new IllegalArgumentException("RAG_MULTIMODAL_EMBEDDING_PROTOCOL_REQUIRED");
        }
        if (vectorStore == null) {
            throw new IllegalArgumentException("RAG_MULTIMODAL_VECTOR_STORE_REQUIRED");
        }
        this.embeddingProtocol = embeddingProtocol;
        this.vectorStore = vectorStore;
    }

    public List<Document> search(
            String query,
            String filterExpression,
            int requestedTopK) throws Exception {
        List<Double> embedding = embeddingProtocol.embedText(query, "query");
        return vectorStore.search(embedding, filterExpression, requestedTopK)
                .stream()
                .map(this::springDocument)
                .toList();
    }

    private Document springDocument(RagDocument document) {
        return new Document(document.id(), document.text(), document.metadata());
    }
}
