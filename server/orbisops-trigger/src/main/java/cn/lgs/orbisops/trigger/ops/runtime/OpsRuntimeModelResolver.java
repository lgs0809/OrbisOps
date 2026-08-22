package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.config.AiClientApiCatalogPort;
import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import cn.lgs.orbisops.application.config.AiClientModelCatalogPort;
import cn.lgs.orbisops.application.config.AiClientModelDefinition;
import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.application.modelpolicy.ModelDefaultPolicyApplicationService;
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
    private final Supplier<AiClientModelCatalogPort> modelRepositorySupplier;
    private final Supplier<AiClientApiCatalogPort> apiRepositorySupplier;
    private final OpsRuntimeDefaultModelSelector defaultModelSelector;
    private final ModelAvailabilityPort aiModelAvailability;
    private final OpsSecretResolver secretResolver;
    private final OpsRuntimeModelSettings settings;

    public OpsRuntimeModelResolver(
            Supplier<ChatModel> namedDefaultModelSupplier,
            Supplier<ChatModel> fallbackModelSupplier,
            Supplier<AiClientModelCatalogPort> modelRepositorySupplier,
            Supplier<AiClientApiCatalogPort> apiRepositorySupplier,
            ModelAvailabilityPort aiModelAvailability,
            OpsSecretResolver secretResolver,
            OpsRuntimeModelSettings settings) {
        this(namedDefaultModelSupplier, fallbackModelSupplier, modelRepositorySupplier, apiRepositorySupplier,
                () -> null, aiModelAvailability, secretResolver, settings);
    }

    public OpsRuntimeModelResolver(
            Supplier<ChatModel> namedDefaultModelSupplier,
            Supplier<ChatModel> fallbackModelSupplier,
            Supplier<AiClientModelCatalogPort> modelRepositorySupplier,
            Supplier<AiClientApiCatalogPort> apiRepositorySupplier,
            Supplier<ModelDefaultPolicyApplicationService> defaultPolicySupplier,
            ModelAvailabilityPort aiModelAvailability,
            OpsSecretResolver secretResolver,
            OpsRuntimeModelSettings settings) {
        this.namedDefaultModelSupplier = required(
                namedDefaultModelSupplier, "RUNTIME_DEFAULT_CHAT_MODEL_SUPPLIER_REQUIRED");
        this.fallbackModelSupplier = required(
                fallbackModelSupplier, "RUNTIME_FALLBACK_CHAT_MODEL_SUPPLIER_REQUIRED");
        this.modelRepositorySupplier = required(
                modelRepositorySupplier, "RUNTIME_MODEL_REPOSITORY_SUPPLIER_REQUIRED");
        this.apiRepositorySupplier = required(
                apiRepositorySupplier, "RUNTIME_API_REPOSITORY_SUPPLIER_REQUIRED");
        this.defaultModelSelector = new OpsRuntimeDefaultModelSelector(defaultPolicySupplier);
        this.aiModelAvailability = required(aiModelAvailability, "AI_MODEL_AVAILABILITY_REQUIRED");
        this.secretResolver = required(secretResolver, "OPS_SECRET_RESOLVER_REQUIRED");
        this.settings = required(settings, "RUNTIME_MODEL_SETTINGS_REQUIRED");
    }

    public ChatModel resolve(OpsRuntimeResourceContext context) {
        String modelId = context == null ? "" : value(context.getModelId());
        if (!StringUtils.hasText(modelId)) {
            modelId = defaultModelSelector.resolve(context);
        }
        if (!StringUtils.hasText(modelId)) {
            return defaultChatModel();
        }
        if (context != null) {
            context.setModelId(modelId);
        }
        AiClientModelCatalogPort modelRepository = modelRepositorySupplier.get();
        AiClientApiCatalogPort apiRepository = apiRepositorySupplier.get();
        if (modelRepository == null || apiRepository == null) {
            warn(context, "Model 配置库不可用，降级默认模型：" + modelId);
            return defaultChatModel();
        }
        AiClientModelDefinition model = modelRepository.findByModelId(modelId);
        if (model == null || !Integer.valueOf(1).equals(model.status())) {
            warn(context, "Model 配置不存在或未启用，降级默认模型：" + context.getModelId());
            return defaultChatModel();
        }
        AiClientApiDefinition api = apiRepository.findByApiId(model.apiId());
        if (api == null || !Integer.valueOf(1).equals(api.status())) {
            warn(context, "Model 关联 API 不存在或未启用，降级默认模型：" + model.apiId());
            return defaultChatModel();
        }
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(api.baseUrl())
                .apiKey(secretResolver.resolve(api.apiKey()))
                .completionsPath(firstText(api.completionsPath(), "v1/chat/completions"))
                .embeddingsPath(firstText(api.embeddingsPath(), "v1/embeddings"))
                .restClientBuilder(OpsModelHttpClientFactory.restClientBuilder(
                        settings.connectTimeoutSeconds(),
                        settings.readTimeoutSeconds()))
                .build();
        context.getMetadata().put("modelName", model.modelName());
        context.getMetadata().put("apiId", api.apiId());
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                // Platform runtime owns retries/time budgets explicitly. Hidden SDK retry/backoff
                // makes one durable model attempt non-observable and can outlive the node budget.
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).noBackoff().build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model.modelName())
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

    private <T> T required(T value, String error) {
        if (value == null) throw new IllegalArgumentException(error);
        return value;
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
