package cn.lgs.orbisops.trigger.ops.channel.wechat;

import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Official Account API client. Access tokens remain provider-local and are never persisted. */
@Component
final class OpsWeChatOfficialAccountClient {

    private static final String API_BASE = "https://api.weixin.qq.com";

    private final OpsSecretResolver secrets;
    private final RestClient http;
    private final Map<String, CachedToken> tokens = new ConcurrentHashMap<>();

    @Autowired
    OpsWeChatOfficialAccountClient(OpsSecretResolver secrets) {
        this(secrets, RestClient.builder().baseUrl(API_BASE).build());
    }

    OpsWeChatOfficialAccountClient(OpsSecretResolver secrets, RestClient http) {
        if (secrets == null) throw new IllegalArgumentException("CHANNEL_SECRET_RESOLVER_REQUIRED");
        if (http == null) throw new IllegalArgumentException("WECHAT_HTTP_CLIENT_REQUIRED");
        this.secrets = secrets;
        this.http = http;
    }

    String token(OpsWeChatChannelConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        CachedToken cached = tokens.get(configuration.appId());
        if (cached != null && cached.usable()) return cached.value();

        String appSecret = secrets.resolve(configuration.credentialRef());
        if (appSecret == null || appSecret.isBlank()) throw new IllegalStateException("WECHAT_APP_SECRET_UNAVAILABLE");
        try {
            Map<?, ?> response = http.get()
                    .uri(builder -> builder
                            .path("/cgi-bin/token")
                            .queryParam("grant_type", "client_credential")
                            .queryParam("appid", configuration.appId())
                            .queryParam("secret", appSecret.trim())
                            .build())
                    .retrieve()
                    .body(Map.class);
            assertAccepted(response, "WECHAT_ACCESS_TOKEN_REJECTED", false);
            String accessToken = text(response == null ? null : response.get("access_token"));
            if (accessToken.isBlank()) throw new IllegalStateException("WECHAT_ACCESS_TOKEN_MISSING");
            long expiresIn = number(response == null ? null : response.get("expires_in"), 7200L);
            CachedToken fresh = new CachedToken(accessToken, Instant.now().plusSeconds(Math.max(60L, expiresIn)));
            tokens.put(configuration.appId(), fresh);
            return fresh.value();
        } catch (IllegalStateException failure) {
            if (failure.getMessage() != null && failure.getMessage().startsWith("WECHAT_")) throw failure;
            throw new IllegalStateException("WECHAT_ACCESS_TOKEN_FAILED:" + failure.getClass().getSimpleName());
        } catch (RuntimeException failure) {
            throw new IllegalStateException("WECHAT_ACCESS_TOKEN_FAILED:" + failure.getClass().getSimpleName());
        }
    }

    ChannelDeliveryReceipt send(OpsWeChatChannelConfiguration configuration, ChannelOutboundMessage message) {
        if (message == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_MESSAGE_REQUIRED");
        String target = message.conversation().externalConversationId();
        String content = message.content().plainText().isBlank()
                ? message.content().markdown()
                : message.content().plainText();
        if (content == null || content.isBlank()) throw new IllegalArgumentException("WECHAT_OUTBOUND_TEXT_REQUIRED");
        String accessToken = token(configuration);
        try {
            Map<?, ?> response = http.post()
                    .uri(builder -> builder
                            .path("/cgi-bin/message/custom/send")
                            .queryParam("access_token", accessToken)
                            .build())
                    .body(Map.of(
                            "touser", target,
                            "msgtype", "text",
                            "text", Map.of("content", content)))
                    .retrieve()
                    .body(Map.class);
            assertAccepted(response, "WECHAT_CUSTOMER_SERVICE_SEND_REJECTED", true);
            return new ChannelDeliveryReceipt(true, "DELIVERED", "", 200, Instant.now());
        } catch (IllegalStateException failure) {
            if (failure.getMessage() != null && failure.getMessage().startsWith("WECHAT_")) throw failure;
            throw new IllegalStateException("WECHAT_CUSTOMER_SERVICE_SEND_FAILED:" + failure.getClass().getSimpleName());
        } catch (RuntimeException failure) {
            throw new IllegalStateException("WECHAT_CUSTOMER_SERVICE_SEND_FAILED:" + failure.getClass().getSimpleName());
        }
    }

    private void assertAccepted(Map<?, ?> response, String reasonCode, boolean requireErrorCode) {
        if (response == null) throw new IllegalStateException(reasonCode + ":EMPTY_RESPONSE");
        if (requireErrorCode && !response.containsKey("errcode")) {
            throw new IllegalStateException(reasonCode + ":ERRCODE_MISSING");
        }
        long errorCode = number(response.get("errcode"), 0L);
        if (errorCode != 0L) {
            throw new IllegalStateException(reasonCode + ":" + errorCode);
        }
    }

    private long number(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        try {
            String normalized = text(value);
            return normalized.isBlank() ? fallback : Long.parseLong(normalized);
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
