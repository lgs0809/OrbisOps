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
            @Value("${orbisops.rag.eval.rerank-enabled:${orbisops.rag.rerank.enabled:false}}") boolean rerankEnabled,
            @Value("${orbisops.rag.eval.rerank-provider:${orbisops.rag.rerank.provider:}}") String rerankProvider,
            @Value("${orbisops.rag.eval.rerank-base-url:${orbisops.rag.rerank.base-url:}}") String rerankBaseUrl,
            @Value("${orbisops.rag.eval.rerank-api-key:${orbisops.rag.rerank.api-key:}}") String rerankApiKey,
            @Value("${orbisops.rag.eval.rerank-path:${orbisops.rag.rerank.path:v1/rerank}}") String rerankPath,
            @Value("${orbisops.rag.eval.rerank-model:${orbisops.rag.rerank.model:}}") String rerankModel) {
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
