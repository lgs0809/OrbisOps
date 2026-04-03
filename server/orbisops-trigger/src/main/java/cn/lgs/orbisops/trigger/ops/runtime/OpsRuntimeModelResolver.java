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
        if (!OpsRuntimeModelRequirement.requiresChatModel(context)) {
            context.getMetadata().put("modelResolution", "NOT_REQUIRED_BY_DETERMINISTIC_NODES");
            return null;
        }
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
            throw new IllegalStateException("MODEL_CATALOG_UNAVAILABLE");
        }
        AiClientModelDefinition model = modelRepository.findByModelId(modelId);
        if (model == null || !Integer.valueOf(1).equals(model.status())) {
            throw new IllegalStateException("MODEL_BINDING_UNAVAILABLE");
        }
        AiClientApiDefinition api = apiRepository.findByApiId(model.apiId());
        if (api == null || !Integer.valueOf(1).equals(api.status())) {
            throw new IllegalStateException("MODEL_PROVIDER_BINDING_UNAVAILABLE");
        }
        OpenAiApi openAiApi = new OpsResilientOpenAiApi(api.baseUrl(), secretResolver.resolve(api.apiKey()),
                firstText(api.completionsPath(), "/v1/chat/completions"),
                firstText(api.embeddingsPath(), "/v1/embeddings"),
                settings.connectTimeoutSeconds(), settings.readTimeoutSeconds(), settings.requestBudgetMillis(), context::record);
        context.getMetadata().put("modelName", model.modelName());
        context.getMetadata().put("apiId", api.apiId());
        context.getMetadata().put("modelBindingHash", cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256(Map.of(
                "modelId",model.modelId(),"modelName",model.modelName(),"apiId",api.apiId(),
                "baseUrl",api.baseUrl(),"path",firstText(api.completionsPath(),"/v1/chat/completions"))));
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                // OpsResilientOpenAiApi owns the observable HTTP retry budget. Never multiply it in the SDK.
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
