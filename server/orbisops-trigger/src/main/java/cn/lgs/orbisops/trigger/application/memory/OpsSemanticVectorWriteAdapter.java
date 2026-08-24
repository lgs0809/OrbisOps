package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.SemanticVectorWritePort;
import cn.lgs.orbisops.domain.memory.model.SemanticMemoryDocumentSnapshot;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/** Spring AI VectorStore adapter for semantic-memory writes. */
@Component
public class OpsSemanticVectorWriteAdapter implements SemanticVectorWritePort {

    private final ObjectProvider<VectorStore> vectorStoreProvider;

    public OpsSemanticVectorWriteAdapter(ObjectProvider<VectorStore> vectorStoreProvider) {
        this.vectorStoreProvider = vectorStoreProvider;
    }

    @Override
    public void writeVector(SemanticMemoryDocumentSnapshot document) {
        VectorStore vectorStore = vectorStoreProvider == null
                ? null
                : vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new IllegalStateException("VectorStore 未初始化");
        }
        vectorStore.add(List.of(new Document(document.id(), document.content(), document.metadata())));
    }
}
