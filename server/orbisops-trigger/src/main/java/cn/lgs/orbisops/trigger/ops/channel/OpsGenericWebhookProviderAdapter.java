package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.application.channel.provider.ChannelAttachment;
import cn.lgs.orbisops.application.channel.provider.ChannelCapabilitySet;
import cn.lgs.orbisops.application.channel.provider.ChannelCapabilitySet.ChannelCapability;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelConversationRef;
import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelExternalPrincipal;
import cn.lgs.orbisops.application.channel.provider.ChannelHealthSnapshot;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundPacket;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveActionEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderAdapter;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderConfiguration;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import cn.lgs.orbisops.application.channel.provider.ChannelType;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Typed ACL around the existing, security-hardened Generic Webhook transport. */
@Component
public final class OpsGenericWebhookProviderAdapter
        implements ChannelProviderAdapter<OpsGenericWebhookProviderAdapter.GenericWebhookConfiguration> {

    private final OpsGenericWebhookChannelAdapter transport;
    private final OpsOutboundUrlPolicy outboundUrlPolicy;

    public OpsGenericWebhookProviderAdapter(OpsGenericWebhookChannelAdapter transport,
                                            OpsOutboundUrlPolicy outboundUrlPolicy) {
        if (transport == null) throw new IllegalArgumentException("CHANNEL_WEBHOOK_TRANSPORT_REQUIRED");
        if (outboundUrlPolicy == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_URL_POLICY_REQUIRED");
        this.transport = transport;
        this.outboundUrlPolicy = outboundUrlPolicy;
    }

    @Override
    public ChannelType type() {
        return ChannelType.GENERIC_WEBHOOK;
    }

    @Override
    public ChannelCapabilitySet capabilities() {
        return ChannelCapabilitySet.of(
                ChannelCapability.INBOUND,
                ChannelCapability.OUTBOUND,
                ChannelCapability.INTERACTIVE_ACTIONS,
                ChannelCapability.ATTACHMENTS,
                ChannelCapability.DIRECT_MESSAGES,
                ChannelCapability.GROUP_MESSAGES,
                ChannelCapability.WEBHOOK,
                ChannelCapability.REPLY_TO_INBOUND);
    }

    @Override
    public GenericWebhookConfiguration configuration(ChannelRecord channel) {
        if (channel == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (ChannelType.parse(channel.channelType()) != type()) {
            throw new IllegalArgumentException("CHANNEL_PROVIDER_TYPE_MISMATCH");
        }
        Map<String, Object> raw = objectMap(channel.config());
        String outboundUrl = text(raw.get("outboundUrl"));
        URI endpoint = outboundUrl.isBlank() ? null : outboundUrlPolicy.validate(outboundUrl);
        return new GenericWebhookConfiguration(channel.channelId(), channel.projectId(), channel.credentialRef(), endpoint,
                Duration.ofSeconds(number(raw.get("timeoutSeconds"), 15)));
    }

    @Override
    public void validateConfiguration(GenericWebhookConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        transport.validateConfiguration(legacyChannel(configuration));
    }

    @Override
    public ChannelHealthSnapshot preflight(GenericWebhookConfiguration configuration) {
        validateConfiguration(configuration);
        if (configuration.outboundUrl() == null) {
            return new ChannelHealthSnapshot(ChannelHealthSnapshot.HealthStatus.DEGRADED,
                    "CHANNEL_OUTBOUND_ENDPOINT_NOT_CONFIGURED", "Inbound configured; outbound endpoint absent", Instant.now());
        }
        return new ChannelHealthSnapshot(ChannelHealthSnapshot.HealthStatus.UNKNOWN,
                "CHANNEL_EXTERNAL_CONNECTIVITY_NOT_PROBED",
                "Configuration is valid; readiness requires an explicit inbound/outbound test", Instant.now());
    }

    @Override
    public ChannelInboundEnvelope parseInbound(GenericWebhookConfiguration configuration, ChannelInboundPacket packet) {
        validateConfiguration(configuration);
        if (packet == null || packet.body().length == 0) throw new IllegalArgumentException("CHANNEL_MESSAGE_REQUIRED");
        OpsChannelMessage message = protocolMessage(JSON.parseObject(new String(packet.body(), StandardCharsets.UTF_8)));
        ChannelConversationRef conversation = new ChannelConversationRef(message.externalConversationId(),
                ChannelConversationRef.ConversationKind.UNKNOWN);
        ChannelMessageRef messageRef = new ChannelMessageRef(message.externalMessageId(), conversation);
        ChannelExternalPrincipal sender = new ChannelExternalPrincipal(message.senderId(), "",
                ChannelExternalPrincipal.PrincipalKind.USER);
        List<ChannelAttachment> attachments = message.attachments().stream()
                .map(item -> new ChannelAttachment(item.attachmentId(), item.fileName(), item.mediaType(), item.sizeBytes(),
                        item.contentRef(), item.contentHash()))
                .toList();
        List<ChannelInteractiveAction> actions = message.action() == null
                ? List.of()
                : List.of(action(message.action()));
        return new ChannelInboundEnvelope(messageRef, sender,
                new ChannelRichContent(message.text(), "", actions), attachments,
                Instant.ofEpochSecond(message.timestamp()), message.externalMessageId());
    }

    @Override
    public ChannelDeliveryReceipt send(GenericWebhookConfiguration configuration, ChannelOutboundMessage message) {
        validateConfiguration(configuration);
        if (message == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_MESSAGE_REQUIRED");
        Map<String, Object> result = transport.send(
                legacyChannel(configuration),
                message.conversation().externalConversationId(),
                outboundContent(message.content()),
                message.metadata().toProtocolMap());
        return new ChannelDeliveryReceipt(Boolean.TRUE.equals(result.get("delivered")),
                text(result.get("status")), text(result.get("providerMessageId")),
                integer(result.get("httpStatus")), Instant.now());
    }

    @Override
    public Optional<ChannelInteractiveActionEnvelope> parseInteractiveAction(GenericWebhookConfiguration configuration,
                                                                              ChannelInboundPacket packet) {
        ChannelInboundEnvelope inbound = parseInbound(configuration, packet);
        if (inbound.content().actions().isEmpty()) return Optional.empty();
        return Optional.of(new ChannelInteractiveActionEnvelope(inbound.content().actions().get(0), inbound.sender(),
                inbound.message(), inbound.receivedAt(), inbound.idempotencyKey()));
    }

    private Map<String, Object> legacyChannel(GenericWebhookConfiguration configuration) {
        Map<String, Object> config = new LinkedHashMap<>();
        if (configuration.outboundUrl() != null) config.put("outboundUrl", configuration.outboundUrl().toString());
        config.put("timeoutSeconds", configuration.timeout().toSeconds());
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("channelId", configuration.channelId());
        channel.put("projectId", configuration.projectId());
        channel.put("channelType", type().name());
        channel.put("credentialRef", configuration.credentialRef());
        channel.put("config", Map.copyOf(config));
        return Map.copyOf(channel);
    }

    private String outboundContent(ChannelRichContent content) {
        return content.markdown().isBlank() ? content.plainText() : content.markdown();
    }

    private ChannelInteractiveAction action(OpsChannelAction source) {
        String token = text(source.value());
        if (token.isBlank()) token = source.actionId();
        String label = text(source.actionType());
        if (label.isBlank()) label = source.actionId();
        return new ChannelInteractiveAction(source.actionId(), label, token, ChannelInteractiveAction.ActionStyle.DEFAULT);
    }

    private OpsChannelMessage protocolMessage(JSONObject source) {
        if (source == null) throw new IllegalArgumentException("CHANNEL_MESSAGE_REQUIRED");
        List<OpsChannelAttachment> attachments = new ArrayList<>();
        JSONArray attachmentArray = source.getJSONArray("attachments");
        if (attachmentArray != null) {
            for (int i = 0; i < attachmentArray.size(); i++) {
                JSONObject item = attachmentArray.getJSONObject(i);
                attachments.add(new OpsChannelAttachment(text(item.get("attachmentId")), text(item.get("fileName")),
                        text(item.get("mediaType")), longValue(item.get("sizeBytes")), text(item.get("contentRef")),
                        text(item.get("contentHash"))));
            }
        }
        OpsChannelAction action = null;
        JSONObject actionObject = source.getJSONObject("action");
        if (actionObject != null) {
            action = new OpsChannelAction(text(actionObject.get("actionId")), text(actionObject.get("actionType")),
                    text(actionObject.get("value")), objectMap(actionObject.get("parameters")));
        }
        return new OpsChannelMessage(text(source.get("externalMessageId")), text(source.get("externalConversationId")),
                text(source.get("senderId")), text(source.get("text")), longValue(source.get("timestamp")),
                objectMap(source.get("metadata")), text(source.get("messageType")), attachments, action);
    }

    private Map<String, Object> objectMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private int number(Object value, int fallback) {
        try {
            return Math.max(1, Math.min(60, Integer.parseInt(String.valueOf(value))));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(text(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record GenericWebhookConfiguration(String channelId,
                                              String projectId,
                                              String credentialRef,
                                              URI outboundUrl,
                                              Duration timeout) implements ChannelProviderConfiguration {
        public GenericWebhookConfiguration {
            channelId = required(channelId, "CHANNEL_ID_REQUIRED");
            projectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
            credentialRef = credentialRef == null ? "" : credentialRef.trim();
            timeout = timeout == null ? Duration.ofSeconds(15) : timeout;
        }

        @Override
        public ChannelType type() {
            return ChannelType.GENERIC_WEBHOOK;
        }

        @Override
        public ChannelConnectionMode connectionMode() {
            return ChannelConnectionMode.WEBHOOK;
        }

        private static String required(String value, String reasonCode) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
            return normalized;
        }
    }
}
