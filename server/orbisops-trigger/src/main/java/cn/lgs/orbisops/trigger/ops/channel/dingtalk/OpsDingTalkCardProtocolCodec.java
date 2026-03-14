package cn.lgs.orbisops.trigger.ops.channel.dingtalk;

import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelExternalPrincipal;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveActionEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pure card-data and Stream callback translation. */
final class OpsDingTalkCardProtocolCodec {

    Map<String, String> cardData(ChannelRichContent content) {
        if (content == null) throw new IllegalArgumentException("CHANNEL_CONTENT_REQUIRED");
        Map<String, String> data = new LinkedHashMap<>();
        String markdown = content.markdown().isBlank() ? content.plainText() : content.markdown();
        data.put("markdown", markdown);
        data.put("resolved", "false");
        data.put("hasActions", Boolean.toString(!content.actions().isEmpty()));
        if (!content.actions().isEmpty()) {
            ChannelInteractiveAction primary = content.actions().get(0);
            data.put("primaryLabel", primary.label());
            data.put("primaryActionToken", primary.opaqueActionToken());
        }
        if (content.actions().size() > 1) {
            ChannelInteractiveAction secondary = content.actions().get(1);
            data.put("secondaryLabel", secondary.label());
            data.put("secondaryActionToken", secondary.opaqueActionToken());
        }
        return Map.copyOf(data);
    }

    Map<String, String> resolvedCardData(String presentation) {
        String text = presentation == null ? "" : presentation.trim();
        return Map.of(
                "markdown", text.isBlank() ? "Decision recorded" : text,
                "resolved", "true",
                "hasActions", "false");
    }

    DecodedAction decodeCallback(String messageString) {
        if (messageString == null || messageString.isBlank()) return DecodedAction.empty();
        JSONObject outer;
        try {
            outer = JSON.parseObject(messageString);
        } catch (RuntimeException ignored) {
            return DecodedAction.empty();
        }
        String actorId = text(outer.get("userId"));
        String spaceId = text(outer.get("spaceId"));
        String outTrackId = text(outer.get("outTrackId"));
        String contentText = text(outer.get("content"));
        if (actorId.isBlank() || spaceId.isBlank() || outTrackId.isBlank() || contentText.isBlank()) {
            return DecodedAction.empty();
        }
        JSONObject content;
        try {
            content = JSON.parseObject(contentText);
        } catch (RuntimeException ignored) {
            return DecodedAction.empty();
        }
        JSONObject privateData = content.getJSONObject("cardPrivateData");
        JSONObject params = privateData == null ? null : privateData.getJSONObject("params");
        String actionToken = params == null ? "" : firstText(params,
                "actionToken", "opaqueActionToken", "actionKey", "action");
        if (actionToken.isBlank()) return DecodedAction.empty();
        ChannelConversationRef conversation = conversation(spaceId);
        if (conversation == null) return DecodedAction.empty();
        ChannelInteractiveActionEnvelope envelope = new ChannelInteractiveActionEnvelope(
                new ChannelInteractiveAction("dingtalk-card-action", "Approval Action", actionToken,
                        ChannelInteractiveAction.ActionStyle.PRIMARY),
                new ChannelExternalPrincipal(actorId, "", ChannelExternalPrincipal.PrincipalKind.USER),
                new ChannelMessageRef(outTrackId, conversation),
                Instant.now(),
                outTrackId + ":" + actorId + ":" + actionToken);
        return new DecodedAction(envelope, outTrackId);
    }

    JSONObject callbackUpdateResponse(String presentation) {
        JSONObject options = new JSONObject();
        options.put("updateCardDataByKey", true);
        JSONObject cardParamMap = new JSONObject();
        resolvedCardData(presentation).forEach(cardParamMap::put);
        JSONObject cardData = new JSONObject();
        cardData.put("cardParamMap", cardParamMap);
        JSONObject response = new JSONObject();
        response.put("cardUpdateOptions", options);
        response.put("cardData", cardData);
        return response;
    }

    private ChannelConversationRef conversation(String spaceId) {
        String groupPrefix = "dtv1.card//IM_GROUP.";
        if (spaceId.startsWith(groupPrefix) && spaceId.length() > groupPrefix.length()) {
            return new ChannelConversationRef("group:" + spaceId.substring(groupPrefix.length()),
                    ChannelConversationRef.ConversationKind.GROUP);
        }
        String userPrefix = "dtv1.card//IM_ROBOT.";
        if (spaceId.startsWith(userPrefix) && spaceId.length() > userPrefix.length()) {
            return new ChannelConversationRef("user:" + spaceId.substring(userPrefix.length()),
                    ChannelConversationRef.ConversationKind.DIRECT);
        }
        return null;
    }

    private String firstText(JSONObject source, String... keys) {
        for (String key : keys) {
            String value = text(source.get(key));
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record DecodedAction(ChannelInteractiveActionEnvelope envelope, String outTrackId) {
        static DecodedAction empty() {
            return new DecodedAction(null, "");
        }
    }
}
