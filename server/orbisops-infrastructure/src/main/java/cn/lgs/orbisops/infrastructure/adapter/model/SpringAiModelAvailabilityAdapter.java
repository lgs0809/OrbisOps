package cn.lgs.orbisops.infrastructure.adapter.model;

import cn.lgs.orbisops.domain.agent.service.model.AiModelAvailability;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Spring configuration and local endpoint readiness adapter for model-call availability. */
@Component
public final class SpringAiModelAvailabilityAdapter implements AiModelAvailability {

    private static final Set<String> PLACEHOLDER_KEYS = Set.of(
            "dev-placeholder-key", "dev-local-placeholder", "placeholder", "dummy",
            "fake-key", "changeme", "change-me", "test");

    @Value("${ops.ai.model-calls-enabled:true}")
    private boolean modelCallsEnabled;

    @Value("${ops.ai.placeholder-api-key:dev-placeholder-key}")
    private String placeholderApiKey;

    @Value("${spring.ai.openai.base-url:}")
    private String openAiBaseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${spring.ai.openai.embedding.base-url:http://127.0.0.1:8110}")
    private String embeddingBaseUrl;

    @Value("${spring.ai.openai.embedding.api-key:local-rag-models}")
    private String embeddingApiKey;

    @Value("${spring.ai.openai.chat.options.model:}")
    private String chatModel;

    @Value("${spring.ai.openai.embedding.options.model:}")
    private String embeddingModel;

    @Value("${ops.rag.rerank.provider:cohere}")
    private String rerankProvider;

    @Value("${ops.rag.rerank.base-url:http://127.0.0.1:8110}")
    private String rerankBaseUrl;

    @Value("${ops.rag.rerank.model:Qwen/Qwen3-VL-Reranker-2B}")
    private String rerankModel;

    @Value("${ops.rag.rerank.api-key:local-rag-models}")
    private String rerankApiKey;

    @Value("${ops.ai.local-model-readiness-cache-ms:10000}")
    private long localModelReadinessCacheMs;

    @Value("${ops.ai.local-model-readiness-timeout-ms:500}")
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
            return subject + "未启用：OPS_AI_MODEL_CALLS_ENABLED=false。需要调用大模型时请开启该开关并配置 OPENAI_API_KEY。";
        }
        return subject + "需要真实模型密钥：当前未配置 OPENAI_API_KEY，或仍使用本地占位 key。请配置 OPENAI_API_KEY 后重试。";
    }

    @Override
    public Map<String, Object> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("modelCallsEnabled", modelCallsEnabled);
        data.put("openAiBaseUrl", openAiBaseUrl);
        data.put("embeddingBaseUrl", embeddingBaseUrl);
        data.put("chatModel", chatModel);
        data.put("embeddingModel", embeddingModel);
        data.put("rerankProvider", rerankProvider);
        data.put("rerankBaseUrl", rerankBaseUrl);
        data.put("rerankModel", rerankModel);
        data.put("openAiApiKeyConfigured", StringUtils.hasText(openAiApiKey));
        data.put("openAiApiKeyUsable", isApiKeyUsable(openAiApiKey));
        data.put("embeddingApiKeyConfigured", StringUtils.hasText(embeddingApiKey));
        data.put("embeddingApiKeyUsable", isApiKeyUsable(embeddingApiKey));
        data.put("embeddingLocalEndpointReady", localEndpointReadyIfRequired(embeddingBaseUrl, embeddingApiKey));
        data.put("rerankApiKeyConfigured", StringUtils.hasText(rerankApiKey));
        data.put("rerankApiKeyUsable", isApiKeyUsable(rerankApiKey));
        data.put("rerankLocalEndpointReady", localEndpointReadyIfRequired(rerankBaseUrl, rerankApiKey));
        data.put("chatAvailable", isChatAvailable());
        data.put("embeddingAvailable", isEmbeddingAvailable());
        data.put("rerankAvailable", isRerankAvailable(rerankApiKey));
        data.put("message", isChatAvailable() ? "模型调用已启用。" : unavailableMessage("大模型功能"));
        return data;
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
        String normalizedKey = apiKey == null ? "" : apiKey.trim().toLowerCase(Locale.ROOT);
        if ("local-rag-models".equals(normalizedKey)) return true;
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
        String value = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "http://127.0.0.1:8110";
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private record CachedEndpointReadiness(boolean ready, long expiresAtMs) {
    }
}
