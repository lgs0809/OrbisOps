package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagVectorWriterPort;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class PgVectorRagWriterAdapter implements RagVectorWriterPort {

    private final ObjectProvider<PgVectorStore> vectorStoreProvider;

    public PgVectorRagWriterAdapter(ObjectProvider<PgVectorStore> vectorStoreProvider) {
        if (vectorStoreProvider == null) throw new IllegalArgumentException("RAG_VECTOR_STORE_PROVIDER_REQUIRED");
        this.vectorStoreProvider = vectorStoreProvider;
    }

    @Override
    public void write(List<RagDocument> documents) {
        PgVectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new IllegalStateException("PgVectorStore 未初始化，无法写入知识库向量");
        }
        List<Document> springDocuments = documents == null ? List.of() : documents.stream()
                .map(document -> new Document(document.id(), document.text(), document.metadata()))
                .toList();
        vectorStore.accept(springDocuments);
    }
}
