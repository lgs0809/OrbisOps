package cn.lgs.orbisops.trigger.ops.channel.telegram;

import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelExternalPrincipal;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveActionEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import com.alibaba.fastjson2.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Pure Telegram Bot API translation. No network, secrets, repositories, or runtime authority. */
final class OpsTelegramProtocolCodec {

    DecodedUpdate decode(OpsTelegramChannelConfiguration configuration, JSONObject update) {
        if (configuration == null || update == null) return DecodedUpdate.empty();
        long updateId = update.getLongValue("update_id");
        JSONObject callback = update.getJSONObject("callback_query");
        if (callback != null) {
            return new DecodedUpdate(updateId, null, interactive(updateId, callback), text(callback.getString("id")));
        }
        JSONObject message = update.getJSONObject("message");
        if (message == null) return new DecodedUpdate(updateId, null, null, "");
        return new DecodedUpdate(updateId, inbound(configuration, updateId, message), null, "");
    }

    String renderText(ChannelRichContent content) {
        if (content == null) return "";
        return !content.plainText().isBlank() ? content.plainText() : content.markdown();
    }

    Map<String, Object> replyMarkup(ChannelRichContent content) {
        if (content == null || content.actions().isEmpty()) return Map.of();
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        for (ChannelInteractiveAction action : content.actions()) {
            byte[] actionData = action.opaqueActionToken().getBytes(StandardCharsets.UTF_8);
            if (actionData.length == 0 || actionData.length > 64) {
                throw new IllegalArgumentException("TELEGRAM_CALLBACK_DATA_TOO_LARGE");
            }
            rows.add(List.of(Map.of(
                    "text", action.label(),
                    "callback_data", action.opaqueActionToken())));
        }
        Map<String, Object> markup = new LinkedHashMap<>();
        markup.put("inline_keyboard", List.copyOf(rows));
        return Map.copyOf(markup);
    }

    private ChannelInboundEnvelope inbound(OpsTelegramChannelConfiguration configuration,
                                           long updateId,
                                           JSONObject message) {
        JSONObject from = message.getJSONObject("from");
        JSONObject chat = message.getJSONObject("chat");
        if (from == null || chat == null || from.getBooleanValue("is_bot")) return null;
        String senderId = numericText(from.get("id"));
        String conversationId = numericText(chat.get("id"));
        String messageId = numericText(message.get("message_id"));
        String chatType = text(chat.getString("type")).toLowerCase(Locale.ROOT);
        boolean direct = "private".equals(chatType);
        String body = text(message.getString("text"));
        if (body.isBlank()) body = text(message.getString("caption"));
        if (senderId.isBlank() || conversationId.isBlank() || messageId.isBlank() || body.isBlank()) return null;
        if (!direct && configuration.requireMention()) {
            if (configuration.botUsername().isBlank() || !mentions(body, configuration.botUsername())) return null;
            body = stripMention(body, configuration.botUsername());
            if (body.isBlank()) return null;
        }
        String threadId = numericText(message.get("message_thread_id"));
        ChannelConversationRef conversation = new ChannelConversationRef(
                conversationId,
                direct ? ChannelConversationRef.ConversationKind.DIRECT : ChannelConversationRef.ConversationKind.GROUP);
        return new ChannelInboundEnvelope(
                new ChannelMessageRef(messageId, conversation, threadId),
                new ChannelExternalPrincipal(senderId, displayName(from), ChannelExternalPrincipal.PrincipalKind.USER),
                ChannelRichContent.text(body),
                List.of(),
                Instant.now(),
                updateKey(updateId, messageId));
    }

    private ChannelInteractiveActionEnvelope interactive(long updateId, JSONObject callback) {
        JSONObject from = callback.getJSONObject("from");
        JSONObject message = callback.getJSONObject("message");
        JSONObject chat = message == null ? null : message.getJSONObject("chat");
        String actionValue = text(callback.getString("data"));
        String senderId = from == null ? "" : numericText(from.get("id"));
        String conversationId = chat == null ? "" : numericText(chat.get("id"));
        String messageId = message == null ? "" : numericText(message.get("message_id"));
        if (actionValue.isBlank() || senderId.isBlank() || conversationId.isBlank() || messageId.isBlank()) return null;
        ChannelConversationRef conversation = new ChannelConversationRef(
                conversationId,
                "private".equalsIgnoreCase(text(chat.getString("type")))
                        ? ChannelConversationRef.ConversationKind.DIRECT
                        : ChannelConversationRef.ConversationKind.GROUP);
        return new ChannelInteractiveActionEnvelope(
                new ChannelInteractiveAction("telegram-callback", "Approval Action", actionValue,
                        ChannelInteractiveAction.ActionStyle.PRIMARY),
                new ChannelExternalPrincipal(senderId, displayName(from), ChannelExternalPrincipal.PrincipalKind.USER),
                new ChannelMessageRef(messageId, conversation, numericText(message.get("message_thread_id"))),
                Instant.now(),
                updateKey(updateId, messageId));
    }

    private boolean mentions(String body, String botUsername) {
        return body.toLowerCase(Locale.ROOT).contains("@" + botUsername.toLowerCase(Locale.ROOT));
    }

    private String stripMention(String body, String botUsername) {
        return body.replaceFirst("(?i)@" + Pattern.quote(botUsername) + "\\b", "").trim();
    }

    private String displayName(JSONObject from) {
        if (from == null) return "";
        String username = text(from.getString("username"));
        if (!username.isBlank()) return username;
        String first = text(from.getString("first_name"));
        String last = text(from.getString("last_name"));
        return (first + " " + last).trim();
    }

    private String updateKey(long updateId, String fallback) {
        return updateId > 0 ? "telegram:update:" + updateId : "telegram:message:" + fallback;
    }

    private String numericText(Object value) {
        if (value == null) return "";
        if (value instanceof Number number) return String.valueOf(number.longValue());
        return text(value);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record DecodedUpdate(long updateId,
                         ChannelInboundEnvelope inbound,
                         ChannelInteractiveActionEnvelope interactive,
                         String callbackQueryId) {
        static DecodedUpdate empty() {
            return new DecodedUpdate(0L, null, null, "");
        }
    }
}
