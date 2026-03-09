package cn.lgs.orbisops.config;

import cn.lgs.orbisops.trigger.ops.runtime.OpsModelHttpClientFactory;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

@Configuration
public class AiAgentConfig {

    @Bean("openAiChatModel")
    @ConditionalOnMissingBean(name = "openAiChatModel")
    public OpenAiChatModel openAiChatModel(@Value("${spring.ai.openai.base-url}") String baseUrl,
                                           @Value("${spring.ai.openai.api-key}") String apiKey,
                                           @Value("${spring.ai.openai.chat.options.model:gpt-5.4-mini}") String chatModelName,
                                           @Value("${spring.ai.openai.chat.connect-timeout-seconds:5}") int connectTimeoutSeconds,
                                           @Value("${spring.ai.openai.chat.read-timeout-seconds:45}") int readTimeoutSeconds) {
        String resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : "dev-placeholder-key";
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(resolvedApiKey)
                .completionsPath("v1/chat/completions")
                .embeddingsPath("v1/embeddings")
                .restClientBuilder(OpsModelHttpClientFactory.restClientBuilder(connectTimeoutSeconds, readTimeoutSeconds))
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(chatModelName)
                        .build())
                .build();
    }

    /**
     * -- 删除旧的表（如果存在）
     * -- 创建新的表，使用UUID作为主键
     * CREATE TABLE public.vector_store_qwen_vl_2048 (
     * id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     * content TEXT NOT NULL,
     * metadata JSONB,
     * embedding VECTOR(2048)
     * );
     * <p>
     * SELECT * FROM vector_store_qwen_vl_2048
     */
    @Bean("ragEmbeddingModel")
    public OpenAiEmbeddingModel ragEmbeddingModel(@Value("${spring.ai.openai.embedding.base-url:http://127.0.0.1:8110}") String embeddingBaseUrl,
                                                  @Value("${spring.ai.openai.embedding.api-key:local-rag-models}") String embeddingApiKey,
                                                  @Value("${spring.ai.openai.embedding.options.model:Qwen/Qwen3-VL-Embedding-2B}") String embeddingModelName,
                                                  @Value("${spring.ai.openai.embedding.options.dimensions:2048}") Integer embeddingDimensions,
                                                  @Value("${spring.ai.openai.chat.connect-timeout-seconds:5}") int connectTimeoutSeconds,
                                                  @Value("${spring.ai.openai.chat.read-timeout-seconds:45}") int readTimeoutSeconds) {
        String resolvedApiKey = StringUtils.hasText(embeddingApiKey) ? embeddingApiKey : "dev-placeholder-key";
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(embeddingBaseUrl)
                .apiKey(resolvedApiKey)
                .embeddingsPath("v1/embeddings")
                .restClientBuilder(OpsModelHttpClientFactory.restClientBuilder(connectTimeoutSeconds, readTimeoutSeconds))
                .build();

        OpenAiEmbeddingOptions embeddingOptions = OpenAiEmbeddingOptions.builder()
                .model(embeddingModelName)
                .dimensions(embeddingDimensions)
                .build();
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, embeddingOptions);
    }

    @Bean("vectorStore")
    @Profile("!test")
    public PgVectorStore pgVectorStore(@Qualifier("ragEmbeddingModel") OpenAiEmbeddingModel embeddingModel,
                                       @Value("${spring.ai.openai.embedding.options.dimensions:2048}") Integer embeddingDimensions,
                                       @Value("${ops.rag.vector-table-name:vector_store_qwen_vl_2048}") String vectorTableName,
                                       @Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate) {

        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName(vectorTableName)
                .dimensions(embeddingDimensions)
                .build();
    }

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

}
