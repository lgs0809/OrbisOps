package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagMultimodalWriterPort;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.trigger.ops.rag.RagMultimodalEmbeddingService;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class OpsRagMultimodalWriterAdapter implements RagMultimodalWriterPort {

    private final ObjectProvider<RagMultimodalEmbeddingService> serviceProvider;

    public OpsRagMultimodalWriterAdapter(ObjectProvider<RagMultimodalEmbeddingService> serviceProvider) {
        if (serviceProvider == null) throw new IllegalArgumentException("RAG_MULTIMODAL_PROVIDER_REQUIRED");
        this.serviceProvider = serviceProvider;
    }

    @Override
    public void write(List<RagDocument> documents, RagFileResource sourceFile) {
        RagMultimodalEmbeddingService service = serviceProvider.getIfAvailable();
        if (service == null) return;
        List<Document> springDocuments = documents == null ? List.of() : documents.stream()
                .map(document -> new Document(document.id(), document.text(), document.metadata()))
                .toList();
        service.storeDocuments(springDocuments, sourceFile);
    }
}
