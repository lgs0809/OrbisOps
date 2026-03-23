package cn.lgs.orbisops.trigger.ops.channel.qq;

import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Component
final class OpsQqApiClient {

    private final OpsQqCredentialExchangeClient credentials;
    private final RestClient http;
    private final OpsQqProtocolCodec codec = new OpsQqProtocolCodec();

    @Autowired
    OpsQqApiClient(OpsQqCredentialExchangeClient credentials) {
        this(credentials, RestClient.builder().baseUrl("https://api.sgroup.qq.com").build());
    }

    OpsQqApiClient(OpsQqCredentialExchangeClient credentials, RestClient http) {
        if (credentials == null) throw new IllegalArgumentException("QQ_CREDENTIAL_EXCHANGE_CLIENT_REQUIRED");
        if (http == null) throw new IllegalArgumentException("QQ_HTTP_CLIENT_REQUIRED");
        this.credentials = credentials;
        this.http = http;
    }

    String gatewayUrl(OpsQqChannelConfiguration configuration) {
        JSONObject response = get(configuration, "/gateway");
        String url = text(response.getString("url"));
        if (url.isBlank()) throw new IllegalStateException("QQ_GATEWAY_URL_UNAVAILABLE");
        return url;
    }

    String gatewayAuthorization(OpsQqChannelConfiguration configuration) {
        return "QQBot " + credentials.exchange(configuration);
    }

    ChannelDeliveryReceipt send(OpsQqChannelConfiguration configuration, ChannelOutboundMessage message) {
        if (message == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_MESSAGE_REQUIRED");
        Target target = target(message.conversation().externalConversationId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", 0);
        body.put("content", codec.renderText(message.content()));
        body.put("msg_seq", ThreadLocalRandom.current().nextInt(1, 1_000_000));
        if (!message.metadata().replyToMessageId().isBlank()) body.put("msg_id", message.metadata().replyToMessageId());
        Map<String, Object> keyboard = codec.keyboard(message.content());
        if (!keyboard.isEmpty()) body.put("keyboard", keyboard);
        JSONObject response = post(configuration, target.path(), body);
        return new ChannelDeliveryReceipt(true, "DELIVERED", text(response.getString("id")), null, Instant.now());
    }

    void acknowledgeInteraction(OpsQqChannelConfiguration configuration, String interactionId) {
        String id = required(interactionId, "QQ_INTERACTION_ID_REQUIRED");
        try {
            http.put().uri("/interactions/{id}", id)
                    .header("Authorization", gatewayAuthorization(configuration))
                    .body(Map.of("code", 0))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException failure) {
            throw new IllegalStateException("QQ_INTERACTION_ACK_FAILED:" + failure.getClass().getSimpleName());
        }
    }

    private JSONObject get(OpsQqChannelConfiguration configuration, String path) {
        try {
            Map<?, ?> raw = http.get().uri(path)
                    .header("Authorization", gatewayAuthorization(configuration))
                    .retrieve().body(Map.class);
            return object(raw);
        } catch (RuntimeException failure) {
            if (failure instanceof IllegalStateException state && state.getMessage() != null && state.getMessage().startsWith("QQ_")) throw state;
            throw new IllegalStateException("QQ_API_FAILED:GET:" + failure.getClass().getSimpleName());
        }
    }

    private JSONObject post(OpsQqChannelConfiguration configuration, String path, Map<String, Object> body) {
        try {
            Map<?, ?> raw = http.post().uri(path)
                    .header("Authorization", gatewayAuthorization(configuration))
                    .body(body).retrieve().body(Map.class);
            return object(raw);
        } catch (RuntimeException failure) {
            if (failure instanceof IllegalStateException state && state.getMessage() != null && state.getMessage().startsWith("QQ_")) throw state;
            throw new IllegalStateException("QQ_API_FAILED:POST:" + failure.getClass().getSimpleName());
        }
    }

    private JSONObject object(Map<?, ?> raw) {
        if (raw == null) throw new IllegalStateException("QQ_API_RESPONSE_REQUIRED");
        return JSON.parseObject(JSON.toJSONString(raw));
    }

    private Target target(String externalConversationId) {
        String value = required(externalConversationId, "QQ_CONVERSATION_ID_REQUIRED");
        if (value.startsWith("c2c:")) {
            return new Target("/v2/users/" + segment(value.substring(4)) + "/messages");
        }
        if (value.startsWith("group:")) {
            return new Target("/v2/groups/" + segment(value.substring(6)) + "/messages");
        }
        throw new IllegalArgumentException("QQ_CONVERSATION_ID_INVALID");
    }

    private String segment(String value) {
        String normalized = required(value, "QQ_TARGET_ID_REQUIRED");
        if (!normalized.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("QQ_TARGET_ID_INVALID");
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

    private record Target(String path) {
    }
}
