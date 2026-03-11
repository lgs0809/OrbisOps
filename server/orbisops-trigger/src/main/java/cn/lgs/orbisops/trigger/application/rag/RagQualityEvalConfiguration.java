package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagEvalRepository;
import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import cn.lgs.orbisops.trigger.ops.rag.RagMultimodalEmbeddingService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagQualityEvalConfiguration {

    @Bean
    public RagQualityEvalSettings ragQualityEvalSettings(
            @Value("${ops.rag.eval.rerank-enabled:true}") boolean rerankEnabled,
            @Value("${ops.rag.eval.rerank-provider:${ops.rag.rerank.provider:cohere}}") String rerankProvider,
            @Value("${ops.rag.eval.rerank-base-url:${ops.rag.rerank.base-url:http://127.0.0.1:8110}}") String rerankBaseUrl,
            @Value("${ops.rag.eval.rerank-api-key:${ops.rag.rerank.api-key:local-rag-models}}") String rerankApiKey,
            @Value("${ops.rag.eval.rerank-path:${ops.rag.rerank.path:v1/rerank}}") String rerankPath,
            @Value("${ops.rag.eval.rerank-model:${ops.rag.rerank.model:Qwen/Qwen3-VL-Reranker-2B}}") String rerankModel) {
        return new RagQualityEvalSettings(
                rerankEnabled,
                rerankProvider,
                rerankBaseUrl,
                rerankApiKey,
                rerankPath,
                rerankModel);
    }

    @Bean
    public OpsRagQualityEvalManagementAssembly opsRagQualityEvalManagementAssembly(
            ObjectProvider<VectorStore> vectorStoreProvider,
            @Qualifier("ragEmbeddingModel") ObjectProvider<EmbeddingModel> embeddingModelProvider,
            IRagEvalRepository ragEvalRepository,
            IRagKnowledgeRepository ragKnowledgeRepository,
            ObjectProvider<RagMultimodalEmbeddingService> multimodalEmbeddingProvider,
            RagQualityEvalSettings settings) {
        return OpsRagQualityEvalManagementAssembly.create(
                vectorStoreProvider,
                embeddingModelProvider,
                ragEvalRepository,
                ragKnowledgeRepository,
                multimodalEmbeddingProvider == null
                        ? null
                        : multimodalEmbeddingProvider.getIfAvailable(),
                settings);
    }
}
