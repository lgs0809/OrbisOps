package cn.lgs.orbisops.trigger.ops.channel.qq;

import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelExternalPrincipal;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveActionEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import com.alibaba.fastjson2.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure QQ Open Platform translation for C2C, group @messages and button interactions. */
final class OpsQqProtocolCodec {

    DecodedDispatch decodeDispatch(String eventType, JSONObject data) {
        if (data == null) return DecodedDispatch.empty();
        String normalized = text(eventType).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "C2C_MESSAGE_CREATE" -> new DecodedDispatch(c2c(data), null, "");
            case "GROUP_AT_MESSAGE_CREATE" -> new DecodedDispatch(group(data), null, "");
            case "INTERACTION_CREATE" -> interaction(data);
            default -> DecodedDispatch.empty();
        };
    }

    String renderText(ChannelRichContent content) {
        if (content == null) return "";
        return content.markdown().isBlank() ? content.plainText() : content.markdown();
    }

    Map<String, Object> keyboard(ChannelRichContent content) {
        if (content == null || content.actions().isEmpty()) return Map.of();
        List<Map<String, Object>> buttons = new ArrayList<>();
        int index = 0;
        for (ChannelInteractiveAction action : content.actions()) {
            String actionValue = action.opaqueActionToken();
            if (actionValue.isBlank()) throw new IllegalArgumentException("QQ_BUTTON_DATA_REQUIRED");
            Map<String, Object> render = Map.of(
                    "label", trim(action.label(), 20),
                    "visited_label", trim(action.label(), 20),
                    "style", action.style() == ChannelInteractiveAction.ActionStyle.DANGER ? 0 : 1);
            Map<String, Object> callback = Map.of(
                    "type", 1,
                    "permission", Map.of("type", 2),
                    "data", actionValue,
                    "click_limit", 1);
            buttons.add(Map.of(
                    "id", "orbisops-" + (++index),
                    "render_data", render,
                    "action", callback,
                    "group_id", "orbisops-approval"));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int offset = 0; offset < buttons.size(); offset += 5) {
            rows.add(Map.of("buttons", List.copyOf(buttons.subList(offset, Math.min(offset + 5, buttons.size())))));
        }
        return Map.of("content", Map.of("rows", List.copyOf(rows)));
    }

    private ChannelInboundEnvelope c2c(JSONObject message) {
        JSONObject author = message.getJSONObject("author");
        String sender = author == null ? "" : text(author.getString("user_openid"));
        String messageId = text(message.getString("id"));
        String body = text(message.getString("content"));
        if (sender.isBlank() || messageId.isBlank() || body.isBlank()) return null;
        ChannelConversationRef conversation = new ChannelConversationRef(
                "c2c:" + sender, ChannelConversationRef.ConversationKind.DIRECT);
        return new ChannelInboundEnvelope(
                new ChannelMessageRef(messageId, conversation),
                new ChannelExternalPrincipal(sender, sender, ChannelExternalPrincipal.PrincipalKind.USER),
                ChannelRichContent.text(body), List.of(), Instant.now(), "qq:message:" + messageId);
    }

    private ChannelInboundEnvelope group(JSONObject message) {
        JSONObject author = message.getJSONObject("author");
        if (author == null || author.getBooleanValue("bot")) return null;
        String sender = text(author.getString("member_openid"));
        String groupId = text(message.getString("group_openid"));
        String messageId = text(message.getString("id"));
        String body = sanitizeGroupText(text(message.getString("content")));
        if (sender.isBlank() || groupId.isBlank() || messageId.isBlank() || body.isBlank()) return null;
        ChannelConversationRef conversation = new ChannelConversationRef(
                "group:" + groupId, ChannelConversationRef.ConversationKind.GROUP);
        return new ChannelInboundEnvelope(
                new ChannelMessageRef(messageId, conversation),
                new ChannelExternalPrincipal(sender, displayName(author), ChannelExternalPrincipal.PrincipalKind.USER),
                ChannelRichContent.text(body), List.of(), Instant.now(), "qq:message:" + messageId);
    }

    private DecodedDispatch interaction(JSONObject event) {
        String interactionId = text(event.getString("id"));
        JSONObject data = event.getJSONObject("data");
        JSONObject resolved = data == null ? null : data.getJSONObject("resolved");
        String actionValue = resolved == null ? "" : text(resolved.getString("button_data"));
        String messageId = resolved == null ? "" : text(resolved.getString("message_id"));
        String groupId = text(event.getString("group_openid"));
        String groupActor = text(event.getString("group_member_openid"));
        String user = text(event.getString("user_openid"));
        String actor = !groupActor.isBlank() ? groupActor : user;
        String conversationId = !groupId.isBlank() ? "group:" + groupId : (user.isBlank() ? "" : "c2c:" + user);
        if (interactionId.isBlank() || actionValue.isBlank() || actor.isBlank() || conversationId.isBlank()) {
            return DecodedDispatch.empty();
        }
        if (messageId.isBlank()) messageId = interactionId;
        ChannelConversationRef conversation = new ChannelConversationRef(
                conversationId,
                groupId.isBlank() ? ChannelConversationRef.ConversationKind.DIRECT : ChannelConversationRef.ConversationKind.GROUP);
        ChannelInteractiveActionEnvelope envelope = new ChannelInteractiveActionEnvelope(
                new ChannelInteractiveAction("qq-button", "Approval Action", actionValue,
                        ChannelInteractiveAction.ActionStyle.PRIMARY),
                new ChannelExternalPrincipal(actor, actor, ChannelExternalPrincipal.PrincipalKind.USER),
                new ChannelMessageRef(messageId, conversation), Instant.now(), "qq:interaction:" + interactionId);
        return new DecodedDispatch(null, envelope, interactionId);
    }

    private String sanitizeGroupText(String body) {
        if (body.isBlank()) return body;
        return body.replaceAll("<@!?[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private String displayName(JSONObject author) {
        String username = text(author.getString("username"));
        return username.isBlank() ? text(author.getString("member_openid")) : username;
    }

    private String trim(String value, int limit) {
        String normalized = text(value);
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record DecodedDispatch(ChannelInboundEnvelope inbound,
                           ChannelInteractiveActionEnvelope interactive,
                           String interactionId) {
        static DecodedDispatch empty() {
            return new DecodedDispatch(null, null, "");
        }
    }
}
