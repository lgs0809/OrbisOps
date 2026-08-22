package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import cn.lgs.orbisops.trigger.ops.OpsProjectKnowledgeScopeResolver;
import cn.lgs.orbisops.trigger.ops.OpsRagKnowledgeRetrievalService;
import cn.lgs.orbisops.trigger.ops.OpsRagKnowledgeRuntimeResources;
import cn.lgs.orbisops.trigger.ops.OpsRagKnowledgeSettings;
import cn.lgs.orbisops.trigger.ops.rag.RagMultimodalEmbeddingService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsRagKnowledgeConfiguration {

    @Bean
    public OpsRagKnowledgeSettings opsRagKnowledgeSettings(
            @Value("${orbisops.rag.rerank.enabled:false}") boolean rerankEnabled,
            @Value("${orbisops.rag.rerank.provider:}") String rerankProvider,
            @Value("${orbisops.rag.rerank.base-url:}") String rerankBaseUrl,
            @Value("${orbisops.rag.rerank.api-key:}") String rerankApiKey,
            @Value("${orbisops.rag.rerank.path:v1/rerank}") String rerankPath,
            @Value("${orbisops.rag.rerank.model:}") String rerankModel,
            @Value("${orbisops.rag.rerank.candidate-top-k:20}") int rerankCandidateTopK,
            @Value("${orbisops.rag.rerank.top-n:6}") int rerankTopN,
            @Value("${orbisops.rag.rerank.max-doc-chars:1200}") int rerankMaxDocChars,
            @Value("${orbisops.rag.query-rewrite.mode:rule}") String queryRewriteMode,
            @Value("${orbisops.rag.query-rewrite.llm-enabled:false}") boolean llmQueryRewriteEnabled,
            @Value("${orbisops.rag.query-rewrite.base-url:${spring.ai.openai.base-url:}}") String llmQueryRewriteBaseUrl,
            @Value("${orbisops.rag.query-rewrite.api-key:${spring.ai.openai.api-key:}}") String llmQueryRewriteApiKey,
            @Value("${orbisops.rag.query-rewrite.path:v1/chat/completions}") String llmQueryRewritePath,
            @Value("${orbisops.rag.query-rewrite.model:${spring.ai.openai.chat.options.model:}}") String llmQueryRewriteModel,
            @Value("${orbisops.rag.query-rewrite.max-queries:4}") int llmQueryRewriteMaxQueries,
            @Value("${orbisops.rag.query-rewrite.timeout-seconds:2}") int llmQueryRewriteTimeoutSeconds,
            @Value("${orbisops.rag.query-rewrite.min-chars:18}") int llmQueryRewriteMinChars,
            @Value("${orbisops.rag.query-rewrite.on-low-recall:true}") boolean llmQueryRewriteOnLowRecall,
            @Value("${orbisops.rag.query-rewrite.low-recall-min-candidates:2}") int llmQueryRewriteLowRecallMinCandidates,
            @Value("${orbisops.multi-agent.fail-on-llm-degradation:false}") boolean failOnLlmDegradation) {
        return new OpsRagKnowledgeSettings(
                rerankEnabled,
                rerankProvider,
                rerankBaseUrl,
                rerankApiKey,
                rerankPath,
                rerankModel,
                rerankCandidateTopK,
                rerankTopN,
                rerankMaxDocChars,
                queryRewriteMode,
                llmQueryRewriteEnabled,
                llmQueryRewriteBaseUrl,
                llmQueryRewriteApiKey,
                llmQueryRewritePath,
                llmQueryRewriteModel,
                llmQueryRewriteMaxQueries,
                llmQueryRewriteTimeoutSeconds,
                llmQueryRewriteMinChars,
                llmQueryRewriteOnLowRecall,
                llmQueryRewriteLowRecallMinCandidates,
                failOnLlmDegradation);
    }

    @Bean
    public OpsRagKnowledgeRetrievalService opsRagKnowledgeRetrievalService(
            IRagKnowledgeRepository repository) {
        return new OpsRagKnowledgeRetrievalService(repository);
    }

    @Bean
    public OpsRagKnowledgeRuntimeResources opsRagKnowledgeRuntimeResources(
            ObjectProvider<VectorStore> vectorStoreProvider,
            ObjectProvider<RagMultimodalEmbeddingService> multimodalEmbeddingServiceProvider,
            @Qualifier("ragEmbeddingModel") ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        return new OpsRagKnowledgeRuntimeResources(
                vectorStoreProvider.getIfAvailable(),
                multimodalEmbeddingServiceProvider.getIfAvailable(),
                embeddingModelProvider.getIfAvailable());
    }

    @Bean
    public OpsProjectKnowledgeScopeResolver opsProjectKnowledgeScopeResolver(
            ObjectProvider<ProjectKnowledgeAuthorizationApplicationService> authorizationServiceProvider) {
        return new OpsProjectKnowledgeScopeResolver(authorizationServiceProvider.getIfAvailable());
    }
}
