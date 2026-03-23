package cn.lgs.orbisops.trigger.ops.channel.telegram;

import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class OpsTelegramBotApiClient {

    private static final String API_BASE = "https://api.telegram.org";
    private final OpsSecretResolver secrets;
    private final RestClient http;
    private final OpsTelegramProtocolCodec codec = new OpsTelegramProtocolCodec();

    @Autowired
    OpsTelegramBotApiClient(OpsSecretResolver secrets) {
        this(secrets, RestClient.builder().baseUrl(API_BASE).build());
    }

    OpsTelegramBotApiClient(OpsSecretResolver secrets, RestClient http) {
        if (secrets == null) throw new IllegalArgumentException("CHANNEL_SECRET_RESOLVER_REQUIRED");
        if (http == null) throw new IllegalArgumentException("TELEGRAM_HTTP_CLIENT_REQUIRED");
        this.secrets = secrets;
        this.http = http;
    }

    String resolveCredential(OpsTelegramChannelConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        String value = secrets.resolve(configuration.credentialRef());
        if (value == null || value.isBlank()) throw new IllegalStateException("TELEGRAM_BOT_TOKEN_UNAVAILABLE");
        return value.trim();
    }

    String botUsername(OpsTelegramChannelConfiguration configuration) {
        JSONObject response = call(configuration, "getMe", Map.of());
        JSONObject result = response.getJSONObject("result");
        return result == null ? "" : text(result.getString("username"));
    }

    List<JSONObject> poll(OpsTelegramChannelConfiguration configuration, long offset) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("offset", Math.max(0L, offset));
        body.put("timeout", 25);
        body.put("allowed_updates", List.of("message", "callback_query"));
        JSONObject response = call(configuration, "getUpdates", body);
        JSONArray result = response.getJSONArray("result");
        if (result == null || result.isEmpty()) return List.of();
        List<JSONObject> updates = new ArrayList<>();
        for (int i = 0; i < result.size(); i++) {
            JSONObject update = result.getJSONObject(i);
            if (update != null) updates.add(update);
        }
        return List.copyOf(updates);
    }

    ChannelDeliveryReceipt send(OpsTelegramChannelConfiguration configuration, ChannelOutboundMessage message) {
        if (message == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_MESSAGE_REQUIRED");
        Map<String, Object> body = messageBody(message);
        JSONObject response = call(configuration, "sendMessage", body);
        JSONObject result = response.getJSONObject("result");
        String messageId = result == null ? "" : numericText(result.get("message_id"));
        return new ChannelDeliveryReceipt(true, "DELIVERED", messageId, null, Instant.now());
    }

    ChannelDeliveryReceipt update(OpsTelegramChannelConfiguration configuration,
                                  ChannelMessageRef existingMessage,
                                  ChannelOutboundMessage message) {
        if (existingMessage == null) throw new IllegalArgumentException("CHANNEL_MESSAGE_REF_REQUIRED");
        if (message == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_MESSAGE_REQUIRED");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", existingMessage.conversation().externalConversationId());
        body.put("message_id", existingMessage.externalMessageId());
        body.put("text", codec.renderText(message.content()));
        Map<String, Object> markup = codec.replyMarkup(message.content());
        if (!markup.isEmpty()) body.put("reply_markup", markup);
        call(configuration, "editMessageText", body);
        return new ChannelDeliveryReceipt(true, "UPDATED", existingMessage.externalMessageId(), null, Instant.now());
    }

    void answerCallback(OpsTelegramChannelConfiguration configuration, String callbackQueryId, String presentation) {
        if (callbackQueryId == null || callbackQueryId.isBlank()) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("callback_query_id", callbackQueryId.trim());
        String safePresentation = text(presentation);
        if (!safePresentation.isBlank()) {
            body.put("text", safePresentation.length() > 180 ? safePresentation.substring(0, 180) : safePresentation);
        }
        call(configuration, "answerCallbackQuery", body);
    }

    private Map<String, Object> messageBody(ChannelOutboundMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", message.conversation().externalConversationId());
        body.put("text", codec.renderText(message.content()));
        if (!message.metadata().replyToMessageId().isBlank()) {
            body.put("reply_parameters", Map.of("message_id", message.metadata().replyToMessageId()));
        }
        Map<String, Object> markup = codec.replyMarkup(message.content());
        if (!markup.isEmpty()) body.put("reply_markup", markup);
        return body;
    }

    private JSONObject call(OpsTelegramChannelConfiguration configuration,
                            String method,
                            Map<String, Object> body) {
        String credential = resolveCredential(configuration);
        try {
            Map<?, ?> raw = http.post()
                    .uri("/bot" + credential + "/" + method)
                    .body(body == null ? Map.of() : body)
                    .retrieve()
                    .body(Map.class);
            JSONObject response = raw == null ? null : JSON.parseObject(JSON.toJSONString(raw));
            if (response == null || !response.getBooleanValue("ok")) {
                throw new IllegalStateException("TELEGRAM_API_REJECTED:" + method);
            }
            return response;
        } catch (RuntimeException failure) {
            if (failure instanceof IllegalStateException state
                    && state.getMessage() != null
                    && state.getMessage().startsWith("TELEGRAM_")) {
                throw state;
            }
            throw new IllegalStateException("TELEGRAM_API_FAILED:" + method + ":" + failure.getClass().getSimpleName());
        }
    }

    private String numericText(Object value) {
        if (value == null) return "";
        if (value instanceof Number number) return String.valueOf(number.longValue());
        return text(value);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
