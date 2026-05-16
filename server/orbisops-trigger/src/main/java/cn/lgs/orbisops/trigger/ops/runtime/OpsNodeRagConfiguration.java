package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.trigger.ops.rag.RagMultimodalEmbeddingService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class OpsNodeRagConfiguration {

    @Bean
    public OpsNodeRagSettings opsNodeRagSettings(Environment environment) {
        return new OpsNodeRagSettings(
                new OpsNodeRagSettings.Rerank(
                        bool(environment, "orbisops.rag.rerank.enabled", false),
                        text(environment, "orbisops.rag.rerank.provider", ""),
                        text(environment, "orbisops.rag.rerank.base-url", ""),
                        text(environment, "orbisops.rag.rerank.api-key", ""),
                        text(environment, "orbisops.rag.rerank.path", "v1/rerank"),
                        text(environment, "orbisops.rag.rerank.model", ""),
                        integer(environment, "orbisops.rag.rerank.candidate-top-k", 20),
                        integer(environment, "orbisops.rag.rerank.top-n", 6),
                        integer(environment, "orbisops.rag.rerank.max-doc-chars", 1200)),
                new OpsNodeRagSettings.Ttft(
                        bool(environment, "orbisops.rag.ttft.optimize-streaming", true),
                        bool(environment, "orbisops.rag.ttft.disable-rerank-on-stream", true),
                        integer(environment, "orbisops.rag.ttft.vector-top-k", 4),
                        integer(environment, "orbisops.rag.ttft.bm25-top-k", 6),
                        integer(environment, "orbisops.rag.ttft.final-top-k", 4),
                        bool(environment, "orbisops.rag.ttft.query-rewrite-enabled", true)),
                new OpsNodeRagSettings.QueryRewrite(
                        text(environment, "orbisops.rag.query-rewrite.mode", "rule"),
                        bool(environment, "orbisops.rag.query-rewrite.llm-enabled", false),
                        fallbackText(environment,
                                "orbisops.rag.query-rewrite.base-url",
                                "spring.ai.openai.base-url", ""),
                        fallbackText(environment,
                                "orbisops.rag.query-rewrite.api-key",
                                "spring.ai.openai.api-key", ""),
                        text(environment, "orbisops.rag.query-rewrite.path", "v1/chat/completions"),
                        fallbackText(environment,
                                "orbisops.rag.query-rewrite.model",
                                "spring.ai.openai.chat.options.model", ""),
                        integer(environment, "orbisops.rag.query-rewrite.max-queries", 4),
                        integer(environment, "orbisops.rag.query-rewrite.timeout-seconds", 2),
                        integer(environment, "orbisops.rag.query-rewrite.min-chars", 18),
                        bool(environment, "orbisops.rag.query-rewrite.on-low-recall", true),
                        integer(environment,
                                "orbisops.rag.query-rewrite.low-recall-min-candidates", 2)),
                bool(environment, "orbisops.multi-agent.fail-on-llm-degradation", false));
    }

    @Bean
    public OpsNodeRagAdvisorFactory opsNodeRagAdvisorFactory(
            ObjectProvider<VectorStore> vectorStoreProvider,
            ObjectProvider<RagMultimodalEmbeddingService> multimodalProvider,
            @Qualifier("ragEmbeddingModel") ObjectProvider<EmbeddingModel> embeddingProvider,
            ModelAvailabilityPort aiModelAvailability,
            IRagKnowledgeRepository ragKnowledgeRepository) {
        return new OpsNodeRagAdvisorFactory(
                vectorStoreProvider::getIfAvailable,
                multimodalProvider::getIfAvailable,
                embeddingProvider::getIfAvailable,
                aiModelAvailability,
                ragKnowledgeRepository);
    }

    private String fallbackText(
            Environment environment,
            String key,
            String fallbackKey,
            String fallback) {
        String value = environment.getProperty(key);
        return value == null
                ? text(environment, fallbackKey, fallback)
                : value;
    }

    private String text(Environment environment, String key, String fallback) {
        return environment.getProperty(key, fallback);
    }

    private boolean bool(Environment environment, String key, boolean fallback) {
        return environment.getProperty(key, Boolean.class, fallback);
    }

    private int integer(Environment environment, String key, int fallback) {
        return environment.getProperty(key, Integer.class, fallback);
    }
}
