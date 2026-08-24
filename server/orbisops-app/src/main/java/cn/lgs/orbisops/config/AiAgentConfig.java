package cn.lgs.orbisops.config;

import cn.lgs.orbisops.trigger.ops.runtime.OpsModelHttpClientFactory;
import cn.lgs.orbisops.trigger.ops.runtime.OpsResilientOpenAiApi;
import org.springframework.retry.support.RetryTemplate;
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
                                           @Value("${spring.ai.openai.chat.options.model:unconfigured}") String chatModelName,
                                           @Value("${spring.ai.openai.chat.connect-timeout-seconds:5}") int connectTimeoutSeconds,
                                           @Value("${spring.ai.openai.chat.read-timeout-seconds:45}") int readTimeoutSeconds) {
        String resolvedApiKey = StringUtils.hasText(apiKey) ? apiKey : "dev-placeholder-key";
        OpenAiApi openAiApi = new OpsResilientOpenAiApi(baseUrl, resolvedApiKey,
                "/v1/chat/completions", "/v1/embeddings", connectTimeoutSeconds, readTimeoutSeconds, 240_000L, null);
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).noBackoff().build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(chatModelName)
                        .build())
                .build();
    }

    /**
     * -- 删除旧的表（如果存在）
     * -- 创建新的表，使用UUID作为主键
     * CREATE TABLE public.orbisops_vector_store (
     * id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
     * content TEXT NOT NULL,
     * metadata JSONB,
     * embedding VECTOR(2048)
     * );
     * <p>
     * SELECT * FROM orbisops_vector_store
     */
    @Bean("ragEmbeddingModel")
    public OpenAiEmbeddingModel ragEmbeddingModel(@Value("${spring.ai.openai.embedding.base-url:}") String embeddingBaseUrl,
                                                  @Value("${spring.ai.openai.embedding.api-key:}") String embeddingApiKey,
                                                  @Value("${spring.ai.openai.embedding.options.model:unconfigured}") String embeddingModelName,
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
        OpenAiEmbeddingModel delegate = new OpenAiEmbeddingModel(openAiApi, MetadataMode.NONE, embeddingOptions);
        return new OpenAiCompatibleEmbeddingModel(delegate);
    }

    @Bean("vectorStore")
    @Profile("!test")
    public PgVectorStore pgVectorStore(@Qualifier("ragEmbeddingModel") OpenAiEmbeddingModel embeddingModel,
                                       @Value("${spring.ai.openai.embedding.options.dimensions:2048}") Integer embeddingDimensions,
                                       @Value("${orbisops.rag.vector-table-name:orbisops_vector_store}") String vectorTableName,
                                       @Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate) {

        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName(vectorTableName)
                .dimensions(embeddingDimensions)
                .indexType(embeddingDimensions > 2000
                        ? PgVectorStore.PgIndexType.NONE
                        : PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
    }

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

}
