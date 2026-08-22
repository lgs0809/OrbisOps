package cn.lgs.orbisops.infrastructure.adapter.model;

import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.application.model.ModelAvailabilitySnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Spring configuration and local endpoint readiness adapter for model-call availability. */
@Component
public final class SpringAiModelAvailabilityAdapter implements ModelAvailabilityPort {

    private static final Set<String> PLACEHOLDER_KEYS = Set.of(
            "dev-placeholder-key", "dev-local-placeholder", "placeholder", "dummy",
            "fake-key", "changeme", "change-me", "test", "[redacted_secret]", "unconfigured");

    @Value("${orbisops.ai.model-calls-enabled:true}")
    private boolean modelCallsEnabled;

    @Value("${orbisops.ai.placeholder-api-key:dev-placeholder-key}")
    private String placeholderApiKey;

    @Value("${spring.ai.openai.base-url:}")
    private String openAiBaseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${spring.ai.openai.embedding.base-url:}")
    private String embeddingBaseUrl;

    @Value("${spring.ai.openai.embedding.api-key:}")
    private String embeddingApiKey;

    @Value("${spring.ai.openai.chat.options.model:}")
    private String chatModel;

    @Value("${spring.ai.openai.embedding.options.model:}")
    private String embeddingModel;

    @Value("${orbisops.rag.rerank.provider:}")
    private String rerankProvider;

    @Value("${orbisops.rag.rerank.base-url:}")
    private String rerankBaseUrl;

    @Value("${orbisops.rag.rerank.model:}")
    private String rerankModel;

    @Value("${orbisops.rag.rerank.api-key:}")
    private String rerankApiKey;

    @Value("${orbisops.ai.local-model-readiness-cache-ms:10000}")
    private long localModelReadinessCacheMs;

    @Value("${orbisops.ai.local-model-readiness-timeout-ms:500}")
    private int localModelReadinessTimeoutMs;

    private final ConcurrentMap<String, CachedEndpointReadiness> localEndpointReadiness = new ConcurrentHashMap<>();

    @Override
    public boolean isChatAvailable() {
        return modelCallsEnabled && isApiKeyUsable(openAiApiKey);
    }

    @Override
    public boolean isEmbeddingAvailable() {
        return modelCallsEnabled
                && isApiKeyUsable(embeddingApiKey)
                && localEndpointReadyIfRequired(embeddingBaseUrl, embeddingApiKey);
    }

    @Override
    public boolean isRerankAvailable(String apiKey) {
        return modelCallsEnabled
                && isApiKeyUsable(apiKey)
                && localEndpointReadyIfRequired(rerankBaseUrl, apiKey);
    }

    @Override
    public boolean isApiKeyUsable(String apiKey) {
        if (!StringUtils.hasText(apiKey)) return false;
        String normalized = apiKey.trim();
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if (PLACEHOLDER_KEYS.contains(lowered)) return false;
        if (StringUtils.hasText(placeholderApiKey) && normalized.equals(placeholderApiKey.trim())) return false;
        return !lowered.contains("placeholder") && !lowered.contains("dummy") && !lowered.contains("fake");
    }

    @Override
    public void assertChatAvailable(String feature) {
        if (!isChatAvailable()) throw new IllegalStateException(unavailableMessage(feature));
    }

    @Override
    public String unavailableMessage(String feature) {
        String subject = StringUtils.hasText(feature) ? feature : "大模型功能";
        if (!modelCallsEnabled) {
            return subject + "未启用：模型调用开关已关闭。需要调用模型时请在 OrbisOps 模型设置中启用并配置 Provider。";
        }
        return subject + "需要可用的模型 Provider：当前未配置有效 API Key，或仍使用占位配置。请在 OrbisOps 模型设置中完成配置后重试。";
    }

    @Override
    public ModelAvailabilitySnapshot snapshot() {
        boolean chatAvailable = isChatAvailable();
        return new ModelAvailabilitySnapshot(
                modelCallsEnabled,
                openAiBaseUrl,
                embeddingBaseUrl,
                chatModel,
                embeddingModel,
                rerankProvider,
                rerankBaseUrl,
                rerankModel,
                StringUtils.hasText(openAiApiKey),
                isApiKeyUsable(openAiApiKey),
                StringUtils.hasText(embeddingApiKey),
                isApiKeyUsable(embeddingApiKey),
                localEndpointReadyIfRequired(embeddingBaseUrl, embeddingApiKey),
                StringUtils.hasText(rerankApiKey),
                isApiKeyUsable(rerankApiKey),
                localEndpointReadyIfRequired(rerankBaseUrl, rerankApiKey),
                chatAvailable,
                isEmbeddingAvailable(),
                isRerankAvailable(rerankApiKey),
                chatAvailable ? "模型调用已启用。" : unavailableMessage("大模型功能"));
    }

    private boolean localEndpointReadyIfRequired(String baseUrl, String apiKey) {
        if (!requiresLocalReadinessProbe(baseUrl, apiKey)) return true;
        String cacheKey = normalizeBaseUrl(baseUrl);
        long now = System.currentTimeMillis();
        CachedEndpointReadiness cached = localEndpointReadiness.get(cacheKey);
        if (cached != null && cached.expiresAtMs() > now) return cached.ready();
        boolean ready = probeLocalReady(cacheKey);
        localEndpointReadiness.put(cacheKey, new CachedEndpointReadiness(
                ready, now + Math.max(1000L, localModelReadinessCacheMs)));
        return ready;
    }

    private boolean requiresLocalReadinessProbe(String baseUrl, String apiKey) {
        if (!isApiKeyUsable(apiKey)) return false;
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.trim().toLowerCase(Locale.ROOT);
        return normalizedBaseUrl.contains("127.0.0.1")
                || normalizedBaseUrl.contains("localhost")
                || normalizedBaseUrl.contains("::1");
    }

    private boolean probeLocalReady(String normalizedBaseUrl) {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(normalizedBaseUrl + "/ready");
            connection = (HttpURLConnection) uri.toURL().openConnection();
            int timeoutMs = Math.max(100, localModelReadinessTimeoutMs);
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            return status >= 200 && status < 300;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "";
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private record CachedEndpointReadiness(boolean ready, long expiresAtMs) {
    }
}
