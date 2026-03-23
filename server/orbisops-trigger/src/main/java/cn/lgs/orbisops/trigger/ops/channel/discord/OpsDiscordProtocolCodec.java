package cn.lgs.orbisops.trigger.ops.channel.discord;

import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelExternalPrincipal;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveActionEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Pure Discord Gateway/REST protocol translation. No network, secrets, repositories, or runtime authority. */
final class OpsDiscordProtocolCodec {

    DecodedDispatch decodeDispatch(OpsDiscordChannelConfiguration configuration,
                                   String botUserId,
                                   String eventType,
                                   JSONObject data) {
        if (configuration == null || data == null) return DecodedDispatch.empty();
        String normalizedType = text(eventType).toUpperCase(Locale.ROOT);
        if ("MESSAGE_CREATE".equals(normalizedType)) {
            return new DecodedDispatch(inbound(configuration, botUserId, data), null, "", "");
        }
        if ("INTERACTION_CREATE".equals(normalizedType)) {
            return interactive(data);
        }
        return DecodedDispatch.empty();
    }

    String renderText(ChannelRichContent content) {
        if (content == null) return "";
        String rendered = content.markdown().isBlank() ? content.plainText() : content.markdown();
        if (rendered.length() > 2000) throw new IllegalArgumentException("DISCORD_MESSAGE_TOO_LARGE");
        return rendered;
    }

    List<Map<String, Object>> components(ChannelRichContent content) {
        if (content == null || content.actions().isEmpty()) return List.of();
        List<Map<String, Object>> buttons = new ArrayList<>();
        for (ChannelInteractiveAction action : content.actions()) {
            String actionValue = action.opaqueActionToken();
            if (actionValue.length() > 100) throw new IllegalArgumentException("DISCORD_CUSTOM_ID_TOO_LARGE");
            buttons.add(Map.of(
                    "type", 2,
                    "style", buttonStyle(action.style()),
                    "label", trimLabel(action.label()),
                    "custom_id", actionValue));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < buttons.size(); index += 5) {
            rows.add(Map.of("type", 1, "components", List.copyOf(buttons.subList(index, Math.min(index + 5, buttons.size())))));
        }
        return List.copyOf(rows);
    }

    private ChannelInboundEnvelope inbound(OpsDiscordChannelConfiguration configuration,
                                           String botUserId,
                                           JSONObject message) {
        JSONObject author = message.getJSONObject("author");
        if (author == null || author.getBooleanValue("bot")) return null;
        String senderId = text(author.getString("id"));
        String conversationId = text(message.getString("channel_id"));
        String messageId = text(message.getString("id"));
        boolean direct = text(message.getString("guild_id")).isBlank();
        String content = text(message.getString("content"));
        if (senderId.isBlank() || conversationId.isBlank() || messageId.isBlank() || content.isBlank()) return null;
        if (!direct && configuration.requireMention()) {
            if (text(botUserId).isBlank() || !mentions(message, botUserId)) return null;
            content = stripMention(content, botUserId);
            if (content.isBlank()) return null;
        }
        ChannelConversationRef conversation = new ChannelConversationRef(
                conversationId,
                direct ? ChannelConversationRef.ConversationKind.DIRECT : ChannelConversationRef.ConversationKind.GROUP);
        return new ChannelInboundEnvelope(
                new ChannelMessageRef(messageId, conversation),
                new ChannelExternalPrincipal(senderId, displayName(author), ChannelExternalPrincipal.PrincipalKind.USER),
                ChannelRichContent.text(content),
                List.of(),
                Instant.now(),
                "discord:message:" + messageId);
    }

    private DecodedDispatch interactive(JSONObject interaction) {
        if (interaction.getIntValue("type") != 3) return DecodedDispatch.empty();
        JSONObject data = interaction.getJSONObject("data");
        JSONObject message = interaction.getJSONObject("message");
        JSONObject member = interaction.getJSONObject("member");
        JSONObject user = member == null ? interaction.getJSONObject("user") : member.getJSONObject("user");
        String interactionId = text(interaction.getString("id"));
        String interactionToken = text(interaction.getString("token"));
        String actionValue = data == null ? "" : text(data.getString("custom_id"));
        String senderId = user == null ? "" : text(user.getString("id"));
        String conversationId = text(interaction.getString("channel_id"));
        if (conversationId.isBlank() && message != null) conversationId = text(message.getString("channel_id"));
        String messageId = message == null ? "" : text(message.getString("id"));
        if (interactionId.isBlank() || interactionToken.isBlank() || actionValue.isBlank()
                || senderId.isBlank() || conversationId.isBlank() || messageId.isBlank()) return DecodedDispatch.empty();
        boolean direct = text(interaction.getString("guild_id")).isBlank();
        ChannelConversationRef conversation = new ChannelConversationRef(
                conversationId,
                direct ? ChannelConversationRef.ConversationKind.DIRECT : ChannelConversationRef.ConversationKind.GROUP);
        ChannelInteractiveActionEnvelope envelope = new ChannelInteractiveActionEnvelope(
                new ChannelInteractiveAction("discord-component", "Approval Action", actionValue,
                        ChannelInteractiveAction.ActionStyle.PRIMARY),
                new ChannelExternalPrincipal(senderId, displayName(user), ChannelExternalPrincipal.PrincipalKind.USER),
                new ChannelMessageRef(messageId, conversation),
                Instant.now(),
                "discord:interaction:" + interactionId);
        return new DecodedDispatch(null, envelope, interactionId, interactionToken);
    }

    private boolean mentions(JSONObject message, String botUserId) {
        JSONArray mentions = message.getJSONArray("mentions");
        if (mentions != null) {
            for (int index = 0; index < mentions.size(); index++) {
                JSONObject user = mentions.getJSONObject(index);
                if (user != null && botUserId.equals(text(user.getString("id")))) return true;
            }
        }
        String content = text(message.getString("content"));
        return content.contains("<@" + botUserId + ">") || content.contains("<@!" + botUserId + ">");
    }

    private String stripMention(String value, String botUserId) {
        String result = value.replaceAll("<@!?" + Pattern.quote(botUserId) + ">", " ");
        return result.replaceAll("\\s+", " ").trim();
    }

    private int buttonStyle(ChannelInteractiveAction.ActionStyle style) {
        if (style == ChannelInteractiveAction.ActionStyle.DANGER) return 4;
        if (style == ChannelInteractiveAction.ActionStyle.PRIMARY) return 1;
        return 2;
    }

    private String trimLabel(String value) {
        String normalized = text(value);
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }

    private String displayName(JSONObject user) {
        if (user == null) return "";
        String globalName = text(user.getString("global_name"));
        if (!globalName.isBlank()) return globalName;
        return text(user.getString("username"));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record DecodedDispatch(ChannelInboundEnvelope inbound,
                           ChannelInteractiveActionEnvelope interactive,
                           String interactionId,
                           String interactionToken) {
        static DecodedDispatch empty() {
            return new DecodedDispatch(null, null, "", "");
        }
    }
}
