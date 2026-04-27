package cn.lgs.orbisops.trigger.ops.channel.dingtalk;

import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Advanced Interactive Card create/update client following DingTalk's createAndDeliver lifecycle. */
@Component
final class OpsDingTalkCardClient {

    private static final String API_BASE = "https://api.dingtalk.com";
    private final OpsDingTalkAccessTokenClient tokens;
    private final RestClient http;
    private final OpsDingTalkCardProtocolCodec codec = new OpsDingTalkCardProtocolCodec();

    @Autowired
    OpsDingTalkCardClient(OpsDingTalkAccessTokenClient tokens) {
        this(tokens, RestClient.builder().baseUrl(API_BASE).build());
    }

    OpsDingTalkCardClient(OpsDingTalkAccessTokenClient tokens, RestClient http) {
        if (tokens == null) throw new IllegalArgumentException("DINGTALK_ACCESS_TOKEN_CLIENT_REQUIRED");
        if (http == null) throw new IllegalArgumentException("DINGTALK_HTTP_CLIENT_REQUIRED");
        this.tokens = tokens;
        this.http = http;
    }

    ChannelDeliveryReceipt create(OpsDingTalkChannelConfiguration configuration,
                                  ChannelOutboundMessage message) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (message == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_MESSAGE_REQUIRED");
        Target target = Target.parse(message.conversation().externalConversationId());
        String outTrackId = "orbisops-" + UUID.randomUUID();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("cardTemplateId", configuration.cardTemplateId());
        request.put("outTrackId", outTrackId);
        request.put("callbackType", "STREAM");
        request.put("cardData", Map.of("cardParamMap", codec.cardData(message.content())));
        request.put("openSpaceId", target.openSpaceId());
        if (target.group()) {
            request.put("imGroupOpenSpaceModel", Map.of("supportForward", true));
            request.put("imGroupOpenDeliverModel", Map.of("robotCode", configuration.robotCode()));
        } else {
            request.put("imRobotOpenSpaceModel", Map.of("supportForward", true));
            request.put("imRobotOpenDeliverModel", Map.of("spaceType", "IM_ROBOT"));
        }
        http.post()
                .uri("/v1.0/card/instances/createAndDeliver")
                .header("x-acs-dingtalk-access-token", tokens.token(configuration))
                .body(request)
                .retrieve()
                .toBodilessEntity();
        return new ChannelDeliveryReceipt(true, "DELIVERED", outTrackId, null, Instant.now());
    }

    ChannelDeliveryReceipt update(OpsDingTalkChannelConfiguration configuration,
                                  ChannelMessageRef existingMessage,
                                  ChannelOutboundMessage message) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (existingMessage == null || existingMessage.externalMessageId().isBlank()) {
            throw new IllegalArgumentException("DINGTALK_CARD_OUT_TRACK_ID_REQUIRED");
        }
        if (message == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_MESSAGE_REQUIRED");
        Map<String, Object> request = Map.of(
                "outTrackId", existingMessage.externalMessageId(),
                "cardData", Map.of("cardParamMap", codec.cardData(message.content())),
                "cardUpdateOptions", Map.of("updateCardDataByKey", true));
        http.put()
                .uri("/v1.0/card/instances")
                .header("x-acs-dingtalk-access-token", tokens.token(configuration))
                .body(request)
                .retrieve()
                .toBodilessEntity();
        return new ChannelDeliveryReceipt(true, "UPDATED", existingMessage.externalMessageId(), null, Instant.now());
    }

    private record Target(boolean group, String value) {
        static Target parse(String raw) {
            String normalized = raw == null ? "" : raw.trim();
            if (normalized.startsWith("group:") && normalized.length() > 6) {
                return new Target(true, normalized.substring(6));
            }
            if (normalized.startsWith("user:") && normalized.length() > 5) {
                return new Target(false, normalized.substring(5));
            }
            throw new IllegalArgumentException("DINGTALK_TARGET_INVALID");
        }

        String openSpaceId() {
            return group ? "dtv1.card//IM_GROUP." + value : "dtv1.card//IM_ROBOT." + value;
        }
    }
}
