package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientApiConfigRepository;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientModelConfigRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;
import cn.lgs.orbisops.domain.agent.service.model.AiModelAvailability;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.function.Supplier;

/** Resolves configured or default ChatModel resources without exposing Spring service location to the assembler. */
public final class OpsRuntimeModelResolver {

    private final Supplier<ChatModel> namedDefaultModelSupplier;
    private final Supplier<ChatModel> fallbackModelSupplier;
    private final Supplier<IAiClientModelConfigRepository> modelRepositorySupplier;
    private final Supplier<IAiClientApiConfigRepository> apiRepositorySupplier;
    private final AiModelAvailability aiModelAvailability;
    private final OpsSecretResolver secretResolver;
    private final OpsRuntimeModelSettings settings;

    public OpsRuntimeModelResolver(
            Supplier<ChatModel> namedDefaultModelSupplier,
            Supplier<ChatModel> fallbackModelSupplier,
            Supplier<IAiClientModelConfigRepository> modelRepositorySupplier,
            Supplier<IAiClientApiConfigRepository> apiRepositorySupplier,
            AiModelAvailability aiModelAvailability,
            OpsSecretResolver secretResolver,
            OpsRuntimeModelSettings settings) {
        if (namedDefaultModelSupplier == null) {
            throw new IllegalArgumentException("RUNTIME_DEFAULT_CHAT_MODEL_SUPPLIER_REQUIRED");
        }
        if (fallbackModelSupplier == null) {
            throw new IllegalArgumentException("RUNTIME_FALLBACK_CHAT_MODEL_SUPPLIER_REQUIRED");
        }
        if (modelRepositorySupplier == null) {
            throw new IllegalArgumentException("RUNTIME_MODEL_REPOSITORY_SUPPLIER_REQUIRED");
        }
        if (apiRepositorySupplier == null) {
            throw new IllegalArgumentException("RUNTIME_API_REPOSITORY_SUPPLIER_REQUIRED");
        }
        if (aiModelAvailability == null) {
            throw new IllegalArgumentException("AI_MODEL_AVAILABILITY_REQUIRED");
        }
        if (secretResolver == null) {
            throw new IllegalArgumentException("OPS_SECRET_RESOLVER_REQUIRED");
        }
        if (settings == null) {
            throw new IllegalArgumentException("RUNTIME_MODEL_SETTINGS_REQUIRED");
        }
        this.namedDefaultModelSupplier = namedDefaultModelSupplier;
        this.fallbackModelSupplier = fallbackModelSupplier;
        this.modelRepositorySupplier = modelRepositorySupplier;
        this.apiRepositorySupplier = apiRepositorySupplier;
        this.aiModelAvailability = aiModelAvailability;
        this.secretResolver = secretResolver;
        this.settings = settings;
    }

    public ChatModel resolve(OpsRuntimeResourceContext context) {
        if (context == null || !StringUtils.hasText(context.getModelId())) {
            return defaultChatModel();
        }
        IAiClientModelConfigRepository modelRepository = modelRepositorySupplier.get();
        IAiClientApiConfigRepository apiRepository = apiRepositorySupplier.get();
        if (modelRepository == null || apiRepository == null) {
            warn(context, "Model 配置库不可用，降级默认模型：" + context.getModelId());
            return defaultChatModel();
        }
        AiClientConfigRecord model = modelRepository.queryByModelId(context.getModelId());
        if (model == null || !Integer.valueOf(1).equals(model.getStatus())) {
            warn(context, "Model 配置不存在或未启用，降级默认模型：" + context.getModelId());
            return defaultChatModel();
        }
        AiClientConfigRecord api = apiRepository.queryByApiId(model.getApiId());
        if (api == null || !Integer.valueOf(1).equals(api.getStatus())) {
            warn(context, "Model 关联 API 不存在或未启用，降级默认模型：" + model.getApiId());
            return defaultChatModel();
        }
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(api.getBaseUrl())
                .apiKey(secretResolver.resolve(api.getApiKey()))
                .completionsPath(firstText(api.getCompletionsPath(), "v1/chat/completions"))
                .embeddingsPath(firstText(api.getEmbeddingsPath(), "v1/embeddings"))
                .restClientBuilder(OpsModelHttpClientFactory.restClientBuilder(
                        settings.connectTimeoutSeconds(),
                        settings.readTimeoutSeconds()))
                .build();
        context.getMetadata().put("modelName", model.getModelName());
        context.getMetadata().put("apiId", api.getApiId());
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                // Platform runtime owns retries/time budgets explicitly. Hidden SDK retry/backoff
                // makes one durable model attempt non-observable and can outlive the node budget.
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).noBackoff().build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model.getModelName())
                        .build())
                .build();
    }

    private ChatModel defaultChatModel() {
        aiModelAvailability.assertChatAvailable("Agent 运行资源装配");
        ChatModel named = resolveSafely(namedDefaultModelSupplier);
        if (named != null) {
            return named;
        }
        ChatModel fallback = resolveSafely(fallbackModelSupplier);
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException("ChatModel 未初始化，无法执行 Agent 任务。");
    }

    private ChatModel resolveSafely(Supplier<ChatModel> supplier) {
        try {
            return supplier.get();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void warn(OpsRuntimeResourceContext context, String summary) {
        context.record(OpsRuntimeEvent.builder()
                .eventType("RESOURCE_WARN")
                .status("SUCCEEDED")
                .summary(summary)
                .payload(Map.of("owner", context.ownerLabel()))
                .build());
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
