package cn.lgs.orbisops.trigger.ops.channel.discord;

import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class OpsDiscordApiClient {

    private static final String API_BASE = "https://discord.com/api/v10";
    private final OpsSecretResolver secrets;
    private final RestClient http;
    private final OpsDiscordProtocolCodec codec = new OpsDiscordProtocolCodec();

    @Autowired
    OpsDiscordApiClient(OpsSecretResolver secrets) {
        this(secrets, RestClient.builder().baseUrl(API_BASE).build());
    }

    OpsDiscordApiClient(OpsSecretResolver secrets, RestClient http) {
        if (secrets == null) throw new IllegalArgumentException("CHANNEL_SECRET_RESOLVER_REQUIRED");
        if (http == null) throw new IllegalArgumentException("DISCORD_HTTP_CLIENT_REQUIRED");
        this.secrets = secrets;
        this.http = http;
    }

    String resolveCredential(OpsDiscordChannelConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        String value = secrets.resolve(configuration.credentialRef());
        if (value == null || value.isBlank()) throw new IllegalStateException("DISCORD_BOT_TOKEN_UNAVAILABLE");
        return value.trim();
    }

    BotIdentity botIdentity(OpsDiscordChannelConfiguration configuration) {
        JSONObject response = botGet(configuration, "/users/@me");
        String id = text(response.getString("id"));
        if (id.isBlank()) throw new IllegalStateException("DISCORD_BOT_ID_UNAVAILABLE");
        return new BotIdentity(id, text(response.getString("username")));
    }

    String gatewayUrl(OpsDiscordChannelConfiguration configuration) {
        JSONObject response = botGet(configuration, "/gateway/bot");
        String url = text(response.getString("url"));
        if (url.isBlank()) throw new IllegalStateException("DISCORD_GATEWAY_URL_UNAVAILABLE");
        return withGatewayParameters(url);
    }

    ChannelDeliveryReceipt send(OpsDiscordChannelConfiguration configuration, ChannelOutboundMessage message) {
        if (message == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_MESSAGE_REQUIRED");
        Map<String, Object> body = messageBody(message, false);
        JSONObject response = botExchange(configuration, "POST",
                "/channels/" + path(message.conversation().externalConversationId()) + "/messages", body);
        return new ChannelDeliveryReceipt(true, "DELIVERED", text(response.getString("id")), null, Instant.now());
    }

    ChannelDeliveryReceipt update(OpsDiscordChannelConfiguration configuration,
                                  ChannelMessageRef existingMessage,
                                  ChannelOutboundMessage message) {
        if (existingMessage == null) throw new IllegalArgumentException("CHANNEL_MESSAGE_REF_REQUIRED");
        if (message == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_MESSAGE_REQUIRED");
        Map<String, Object> body = messageBody(message, true);
        botExchange(configuration, "PATCH",
                "/channels/" + path(existingMessage.conversation().externalConversationId())
                        + "/messages/" + path(existingMessage.externalMessageId()), body);
        return new ChannelDeliveryReceipt(true, "UPDATED", existingMessage.externalMessageId(), null, Instant.now());
    }

    void deferComponent(String interactionId, String interactionCredential) {
        String safeId = path(interactionId);
        String safeCredential = required(interactionCredential, "DISCORD_INTERACTION_CREDENTIAL_REQUIRED");
        try {
            http.post()
                    .uri("/interactions/{id}/{credential}/callback", safeId, safeCredential)
                    .body(Map.of("type", 6))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException failure) {
            throw new IllegalStateException("DISCORD_INTERACTION_ACK_FAILED:" + failure.getClass().getSimpleName());
        }
    }

    private JSONObject botGet(OpsDiscordChannelConfiguration configuration, String uri) {
        String credential = resolveCredential(configuration);
        try {
            Map<?, ?> raw = http.get()
                    .uri(uri)
                    .header("Authorization", "Bot " + credential)
                    .retrieve()
                    .body(Map.class);
            return object(raw, "DISCORD_API_RESPONSE_REQUIRED");
        } catch (RuntimeException failure) {
            if (failure instanceof IllegalStateException state && state.getMessage() != null
                    && state.getMessage().startsWith("DISCORD_")) throw state;
            throw new IllegalStateException("DISCORD_API_FAILED:GET:" + failure.getClass().getSimpleName());
        }
    }

    private JSONObject botExchange(OpsDiscordChannelConfiguration configuration,
                                   String method,
                                   String uri,
                                   Map<String, Object> body) {
        String credential = resolveCredential(configuration);
        try {
            Map<?, ?> raw;
            if ("PATCH".equals(method)) {
                raw = http.patch().uri(uri).header("Authorization", "Bot " + credential)
                        .body(body).retrieve().body(Map.class);
            } else {
                raw = http.post().uri(uri).header("Authorization", "Bot " + credential)
                        .body(body).retrieve().body(Map.class);
            }
            return object(raw, "DISCORD_API_RESPONSE_REQUIRED");
        } catch (RuntimeException failure) {
            if (failure instanceof IllegalStateException state && state.getMessage() != null
                    && state.getMessage().startsWith("DISCORD_")) throw state;
            throw new IllegalStateException("DISCORD_API_FAILED:" + method + ":" + failure.getClass().getSimpleName());
        }
    }

    private Map<String, Object> messageBody(ChannelOutboundMessage message, boolean editing) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", codec.renderText(message.content()));
        body.put("allowed_mentions", Map.of("parse", List.of()));
        List<Map<String, Object>> components = codec.components(message.content());
        if (editing || !components.isEmpty()) body.put("components", components);
        if (!editing && !message.metadata().replyToMessageId().isBlank()) {
            body.put("message_reference", Map.of(
                    "message_id", message.metadata().replyToMessageId(),
                    "fail_if_not_exists", false));
        }
        return body;
    }

    private JSONObject object(Map<?, ?> raw, String reasonCode) {
        if (raw == null) throw new IllegalStateException(reasonCode);
        return JSON.parseObject(JSON.toJSONString(raw));
    }

    private String withGatewayParameters(String value) {
        String separator = value.contains("?") ? "&" : "?";
        return value + separator + "v=10&encoding=json";
    }

    private String path(String value) {
        String normalized = required(value, "DISCORD_PATH_ID_REQUIRED");
        if (!normalized.matches("[0-9]+")) throw new IllegalArgumentException("DISCORD_PATH_ID_INVALID");
        return normalized;
    }

    private String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record BotIdentity(String id, String username) {
    }
}
