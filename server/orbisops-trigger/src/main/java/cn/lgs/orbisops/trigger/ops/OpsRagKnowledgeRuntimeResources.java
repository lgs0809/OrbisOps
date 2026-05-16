package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.trigger.ops.rag.RagMultimodalEmbeddingService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

/** Optional framework resources assembled at the Spring wiring boundary. */
public record OpsRagKnowledgeRuntimeResources(
        VectorStore vectorStore,
        RagMultimodalEmbeddingService multimodalEmbeddingService,
        EmbeddingModel embeddingModel) {

    public static OpsRagKnowledgeRuntimeResources unavailable() {
        return new OpsRagKnowledgeRuntimeResources(null, null, null);
    }

    public boolean vectorStoreAvailable() {
        return vectorStore != null;
    }
}
