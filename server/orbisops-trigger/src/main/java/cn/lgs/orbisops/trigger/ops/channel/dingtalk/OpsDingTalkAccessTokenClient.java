package cn.lgs.orbisops.trigger.ops.channel.dingtalk;

import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Provider-local OAuth client. Tokens never leave the DingTalk adapter boundary. */
@Component
final class OpsDingTalkAccessTokenClient {

    private static final String API_BASE = "https://api.dingtalk.com";
    private final OpsSecretResolver secrets;
    private final RestClient http;
    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    @Autowired
    OpsDingTalkAccessTokenClient(OpsSecretResolver secrets) {
        this(secrets, RestClient.builder().baseUrl(API_BASE).build());
    }

    OpsDingTalkAccessTokenClient(OpsSecretResolver secrets, RestClient http) {
        if (secrets == null) throw new IllegalArgumentException("CHANNEL_SECRET_RESOLVER_REQUIRED");
        if (http == null) throw new IllegalArgumentException("DINGTALK_HTTP_CLIENT_REQUIRED");
        this.secrets = secrets;
        this.http = http;
    }

    String token(OpsDingTalkChannelConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        String cacheKey = configuration.corpId() + "\n" + configuration.clientId();
        CachedToken existing = cache.get(cacheKey);
        if (existing != null && existing.usable()) return existing.value();

        String clientSecret = secrets.resolve(configuration.credentialRef());
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("DINGTALK_CLIENT_SECRET_UNAVAILABLE");
        }
        Map<?, ?> response = http.post()
                .uri("/v1.0/oauth2/{corpId}/token", configuration.corpId())
                .body(Map.of(
                        "client_id", configuration.clientId(),
                        "client_secret", clientSecret,
                        "grant_type", "client_credentials"))
                .retrieve()
                .body(Map.class);
        String accessToken = text(value(response, "access_token", "accessToken"));
        if (accessToken.isBlank()) throw new IllegalStateException("DINGTALK_ACCESS_TOKEN_MISSING");
        long expiresIn = number(value(response, "expires_in", "expireIn"), 7200L);
        CachedToken fresh = new CachedToken(accessToken, Instant.now().plusSeconds(Math.max(60L, expiresIn)));
        cache.put(cacheKey, fresh);
        return fresh.value();
    }

    private Object value(Map<?, ?> source, String primary, String compatibility) {
        if (source == null) return null;
        Object value = source.get(primary);
        return value == null ? source.get(compatibility) : value;
    }

    private long number(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        try {
            String text = text(value);
            return text.isBlank() ? fallback : Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record CachedToken(String value, Instant expiresAt) {
        private boolean usable() {
            return value != null && !value.isBlank() && expiresAt != null
                    && Instant.now().plusSeconds(120).isBefore(expiresAt);
        }
    }
}
