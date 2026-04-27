package cn.lgs.orbisops.trigger.ops.channel.qq;

import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
final class OpsQqCredentialExchangeClient {
    private final OpsSecretResolver secrets;
    private final RestClient http;
    private final Map<String, CredentialSession> cache = new ConcurrentHashMap<>();

    @Autowired
    OpsQqCredentialExchangeClient(OpsSecretResolver secrets) {
        this(secrets, RestClient.builder().baseUrl("https://bots.qq.com").build());
    }

    OpsQqCredentialExchangeClient(OpsSecretResolver secrets, RestClient http) {
        if (secrets == null) throw new IllegalArgumentException("CHANNEL_SECRET_RESOLVER_REQUIRED");
        if (http == null) throw new IllegalArgumentException("QQ_CREDENTIAL_HTTP_CLIENT_REQUIRED");
        this.secrets = secrets;
        this.http = http;
    }
    String exchange(OpsQqChannelConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        String cacheKey = configuration.appId() + "|" + configuration.credentialRef();
        CredentialSession cached = cache.get(cacheKey);
        if (cached != null && cached.validAt(Instant.now())) return cached.value();
        String credential = secrets.resolve(configuration.credentialRef());
        if (credential == null || credential.isBlank()) throw new IllegalStateException("QQ_APP_CREDENTIAL_UNAVAILABLE");
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("appId", configuration.appId());
        request.put("clientSecret", credential.trim());
        try {
            Map<?, ?> raw = http.post().uri("/app/getAppAccessToken")
                    .body(request).retrieve().body(Map.class);
            JSONObject response = raw == null ? null : JSON.parseObject(JSON.toJSONString(raw));
            String value = response == null ? "" : text(response.getString("access_token"));
            long expiresIn = response == null ? 0L : response.getLongValue("expires_in");
            if (value.isBlank() || expiresIn <= 0) throw new IllegalStateException("QQ_CREDENTIAL_EXCHANGE_RESPONSE_INVALID");
            CredentialSession session = new CredentialSession(value, Instant.now().plusSeconds(Math.max(30L, expiresIn - 60L)));
            cache.put(cacheKey, session);
            return session.value();
        } catch (RuntimeException failure) {
            if (failure instanceof IllegalStateException state && state.getMessage() != null && state.getMessage().startsWith("QQ_")) throw state;
            throw new IllegalStateException("QQ_CREDENTIAL_EXCHANGE_FAILED:" + failure.getClass().getSimpleName());
        }
    }

    void invalidate(OpsQqChannelConfiguration configuration) {
        if (configuration != null) cache.remove(configuration.appId() + "|" + configuration.credentialRef());
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record CredentialSession(String value, Instant expiresAt) {
        boolean validAt(Instant now) {
            return value != null && !value.isBlank() && expiresAt != null && expiresAt.isAfter(now);
        }
    }
}
