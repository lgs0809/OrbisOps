package cn.lgs.orbisops.trigger.ops.channel.slack;

import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelExternalPrincipal;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveActionEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.slack.api.model.block.ActionsBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.model.block.composition.PlainTextObject;
import com.slack.api.model.block.element.ButtonElement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Pure Slack protocol translation. No network, secrets, repositories, or runtime authority. */
final class OpsSlackProtocolCodec {

    DecodedEnvelope decode(OpsSlackChannelConfiguration configuration, String raw) {
        if (configuration == null || raw == null || raw.isBlank()) return DecodedEnvelope.empty();
        JSONObject root;
        try {
            root = JSON.parseObject(raw);
        } catch (RuntimeException ignored) {
            return DecodedEnvelope.empty();
        }
        if (root == null) return DecodedEnvelope.empty();
        String envelopeId = text(root.getString("envelope_id"));
        JSONObject payload = root.getJSONObject("payload");
        if (payload == null) return new DecodedEnvelope(envelopeId, null, null, List.of());
        String type = text(root.getString("type"));
        if ("events_api".equalsIgnoreCase(type)) {
            List<SlackFileDescriptor> files = files(payload);
            return new DecodedEnvelope(envelopeId, inbound(configuration, payload, envelopeId, files), null, files);
        }
        if ("interactive".equalsIgnoreCase(type)) {
            return new DecodedEnvelope(envelopeId, null, interactive(payload, envelopeId), List.of());
        }
        return new DecodedEnvelope(envelopeId, null, null, List.of());
    }

    List<LayoutBlock> blocks(ChannelRichContent content) {
        if (content == null || content.actions().isEmpty()) return List.of();
        List<LayoutBlock> result = new ArrayList<>();
        result.add(SectionBlock.builder()
                .text(MarkdownTextObject.builder().text(markdown(content)).build())
                .build());
        List<ButtonElement> buttons = content.actions().stream()
                .map(action -> ButtonElement.builder()
                        .actionId("orbisops:" + action.actionId())
                        .text(PlainTextObject.builder().text(action.label()).build())
                        .value(action.opaqueActionToken())
                        .build())
                .toList();
        result.add(ActionsBlock.builder().elements(new ArrayList<>(buttons)).build());
        return List.copyOf(result);
    }

    String markdown(ChannelRichContent content) {
        if (content == null) return "";
        return content.markdown().isBlank() ? content.plainText() : content.markdown();
    }

    private ChannelInboundEnvelope inbound(OpsSlackChannelConfiguration configuration,
                                           JSONObject payload,
                                           String envelopeId,
                                           List<SlackFileDescriptor> files) {
        JSONObject event = payload.getJSONObject("event");
        if (event == null) return null;
        String eventType = text(event.getString("type"));
        if (!("message".equals(eventType) || "app_mention".equals(eventType))) return null;
        if (!text(event.getString("bot_id")).isBlank()
                || "bot_message".equalsIgnoreCase(text(event.getString("subtype")))) return null;
        String userId = text(event.getString("user"));
        String channelId = text(event.getString("channel"));
        String ts = text(event.getString("ts"));
        String content = stripMention(text(event.getString("text")));
        if (content.isBlank() && files != null && !files.isEmpty()) {
            content = attachmentSummary(files);
        }
        if (userId.isBlank() || channelId.isBlank() || ts.isBlank() || content.isBlank()) return null;
        boolean direct = "im".equalsIgnoreCase(text(event.getString("channel_type")));
        if (!direct && configuration.requireMention() && !"app_mention".equals(eventType)) return null;
        String providerThreadId = text(event.getString("thread_ts"));
        String threadId = providerThreadId.isBlank() && !direct ? ts : providerThreadId;
        ChannelConversationRef conversation = new ChannelConversationRef(channelId,
                direct ? ChannelConversationRef.ConversationKind.DIRECT : ChannelConversationRef.ConversationKind.GROUP);
        return new ChannelInboundEnvelope(
                new ChannelMessageRef(ts, conversation, threadId),
                new ChannelExternalPrincipal(userId, "", ChannelExternalPrincipal.PrincipalKind.USER),
                ChannelRichContent.text(content),
                List.of(),
                Instant.now(),
                envelopeId.isBlank() ? ts : envelopeId);
    }

    private List<SlackFileDescriptor> files(JSONObject payload) {
        JSONObject event = payload == null ? null : payload.getJSONObject("event");
        JSONArray rows = event == null ? null : event.getJSONArray("files");
        if (rows == null || rows.isEmpty()) return List.of();
        List<SlackFileDescriptor> result = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            JSONObject file = rows.getJSONObject(index);
            if (file == null) continue;
            String id = text(file.getString("id"));
            if (id.isBlank()) continue;
            String name = text(file.getString("name"));
            if (name.isBlank()) name = id;
            String mediaType = text(file.getString("mimetype"));
            if (mediaType.isBlank()) mediaType = "application/octet-stream";
            long size = file.getLongValue("size");
            String downloadUrl = text(file.getString("url_private_download"));
            if (downloadUrl.isBlank()) downloadUrl = text(file.getString("url_private"));
            result.add(new SlackFileDescriptor(
                    id, name, mediaType, Math.max(0L, size), downloadUrl,
                    "check_file_info".equalsIgnoreCase(text(file.getString("file_access")))));
        }
        return List.copyOf(result);
    }

    private String attachmentSummary(List<SlackFileDescriptor> files) {
        String joined = files.stream().limit(8).map(SlackFileDescriptor::fileName)
                .reduce((left, right) -> left + ", " + right).orElse("attachment");
        return "Shared attachment" + (files.size() == 1 ? ": " : "s: ") + joined;
    }

    private ChannelInteractiveActionEnvelope interactive(JSONObject payload, String envelopeId) {
        if (!"block_actions".equalsIgnoreCase(text(payload.getString("type")))) return null;
        JSONArray actions = payload.getJSONArray("actions");
        JSONObject action = actions == null || actions.isEmpty() ? null : actions.getJSONObject(0);
        JSONObject user = payload.getJSONObject("user");
        JSONObject channel = payload.getJSONObject("channel");
        JSONObject message = payload.getJSONObject("message");
        String actionKey = action == null ? "" : text(action.getString("value"));
        String externalPrincipalId = user == null ? "" : text(user.getString("id"));
        String conversationId = channel == null ? "" : text(channel.getString("id"));
        String messageTs = message == null ? "" : text(message.getString("ts"));
        if (actionKey.isBlank() || externalPrincipalId.isBlank() || conversationId.isBlank() || messageTs.isBlank()) {
            return null;
        }
        ChannelConversationRef conversation = new ChannelConversationRef(
                conversationId, ChannelConversationRef.ConversationKind.UNKNOWN);
        return new ChannelInteractiveActionEnvelope(
                new ChannelInteractiveAction("slack-block-action", "Approval Action", actionKey,
                        ChannelInteractiveAction.ActionStyle.PRIMARY),
                new ChannelExternalPrincipal(externalPrincipalId, "", ChannelExternalPrincipal.PrincipalKind.USER),
                new ChannelMessageRef(messageTs, conversation),
                Instant.now(),
                envelopeId.isBlank() ? messageTs + ":" + externalPrincipalId + ":" + actionKey : envelopeId);
    }

    private String stripMention(String value) {
        if (value == null) return "";
        return value.replaceFirst("^\\s*<@[A-Z0-9]+>\\s*", "").trim();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record SlackFileDescriptor(String fileId,
                               String fileName,
                               String mediaType,
                               long sizeBytes,
                               String downloadUrl,
                               boolean requiresFileInfo) {
    }

    record DecodedEnvelope(String envelopeId,
                           ChannelInboundEnvelope inbound,
                           ChannelInteractiveActionEnvelope interactive,
                           List<SlackFileDescriptor> files) {
        DecodedEnvelope {
            files = files == null ? List.of() : List.copyOf(files);
        }

        static DecodedEnvelope empty() {
            return new DecodedEnvelope("", null, null, List.of());
        }
    }
}
