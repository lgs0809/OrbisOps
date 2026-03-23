package cn.lgs.orbisops.trigger.ops.channel.wecom;

import cn.lgs.orbisops.application.channel.provider.ChannelCapabilitySet;
import cn.lgs.orbisops.application.channel.provider.ChannelCapabilitySet.ChannelCapability;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelHealthSnapshot;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundPacket;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderAdapter;
import cn.lgs.orbisops.application.channel.provider.ChannelType;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Component
public final class OpsWeComProviderAdapter implements ChannelProviderAdapter<OpsWeComChannelConfiguration> {

    private final OpsSecretResolver secrets;
    private final OpsWeComChannelConnectionDriver driver;
    private final OpsWeComProtocolCodec codec = new OpsWeComProtocolCodec();

    public OpsWeComProviderAdapter(OpsSecretResolver secrets,
                                   OpsWeComChannelConnectionDriver driver) {
        if (secrets == null) throw new IllegalArgumentException("CHANNEL_SECRET_RESOLVER_REQUIRED");
        if (driver == null) throw new IllegalArgumentException("WECOM_CONNECTION_DRIVER_REQUIRED");
        this.secrets = secrets;
        this.driver = driver;
    }

    @Override
    public ChannelType type() {
        return ChannelType.WECOM;
    }

    @Override
    public ChannelCapabilitySet capabilities() {
        return ChannelCapabilitySet.of(
                ChannelCapability.INBOUND,
                ChannelCapability.OUTBOUND,
                ChannelCapability.INTERACTIVE_ACTIONS,
                ChannelCapability.DIRECT_MESSAGES,
                ChannelCapability.GROUP_MESSAGES,
                ChannelCapability.LONG_CONNECTION,
                ChannelCapability.PROACTIVE_PUSH,
                ChannelCapability.REPLY_TO_INBOUND);
    }

    @Override
    public OpsWeComChannelConfiguration configuration(ChannelRecord channel) {
        if (channel == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (ChannelType.parse(channel.channelType()) != type()) throw new IllegalArgumentException("CHANNEL_PROVIDER_TYPE_MISMATCH");
        return new OpsWeComChannelConfiguration(
                channel.channelId(), channel.projectId(), channel.credentialRef(),
                required(channel.config().get("botId"), "WECOM_BOT_ID_REQUIRED"),
                connectionMode(channel.config().get("connectionMode")));
    }

    @Override
    public void validateConfiguration(OpsWeComChannelConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (!secrets.isReference(configuration.credentialRef())) {
            throw new IllegalArgumentException("WECOM_BOT_SECRET_MUST_USE_CREDENTIAL_REF");
        }
        if (configuration.connectionMode() != ChannelConnectionMode.LONG_CONNECTION) {
            throw new IllegalArgumentException("WECOM_CONNECTION_MODE_UNSUPPORTED:" + configuration.connectionMode());
        }
    }

    @Override
    public ChannelHealthSnapshot preflight(OpsWeComChannelConfiguration configuration) {
        validateConfiguration(configuration);
        String secret = secrets.resolve(configuration.credentialRef());
        if (secret == null || secret.isBlank()) {
            return new ChannelHealthSnapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                    "WECOM_BOT_SECRET_UNAVAILABLE", "Credential reference is configured but unresolved", Instant.now());
        }
        ChannelHealthSnapshot current = driver.health(configuration.channelId());
        if (current.status() == ChannelHealthSnapshot.HealthStatus.STOPPED) {
            return new ChannelHealthSnapshot(ChannelHealthSnapshot.HealthStatus.UNKNOWN,
                    "WECOM_CONNECTION_NOT_STARTED", "Credentials are resolvable; connection supervisor has not authenticated WebSocket yet", Instant.now());
        }
        return current;
    }

    @Override
    public ChannelInboundEnvelope parseInbound(OpsWeComChannelConfiguration configuration, ChannelInboundPacket packet) {
        validateConfiguration(configuration);
        if (packet == null || packet.body().length == 0) throw new IllegalArgumentException("WECOM_FRAME_REQUIRED");
        return codec.inbound(new String(packet.body(), java.nio.charset.StandardCharsets.UTF_8))
                .map(OpsWeComProtocolCodec.InboundFrame::envelope)
                .orElseThrow(() -> new IllegalArgumentException("WECOM_INBOUND_FRAME_UNSUPPORTED"));
    }

    @Override
    public ChannelDeliveryReceipt send(OpsWeComChannelConfiguration configuration, ChannelOutboundMessage message) {
        validateConfiguration(configuration);
        return driver.send(configuration, message);
    }

    @Override
    public ChannelDeliveryReceipt updateMessage(OpsWeComChannelConfiguration configuration,
                                                ChannelMessageRef existingMessage,
                                                ChannelOutboundMessage message) {
        validateConfiguration(configuration);
        if (existingMessage == null) throw new IllegalArgumentException("WECOM_MESSAGE_REF_REQUIRED");
        String title = message.content().plainText().isBlank() ? message.content().markdown() : message.content().plainText();
        return driver.updateCard(configuration, existingMessage.externalMessageId(), title);
    }

    @Override
    public java.util.Optional<cn.lgs.orbisops.application.channel.provider.ChannelInteractiveActionEnvelope> parseInteractiveAction(
            OpsWeComChannelConfiguration configuration,
            ChannelInboundPacket packet) {
        ChannelInboundEnvelope envelope = parseInbound(configuration, packet);
        if (envelope.content().actions().isEmpty()) return java.util.Optional.empty();
        return java.util.Optional.of(new cn.lgs.orbisops.application.channel.provider.ChannelInteractiveActionEnvelope(
                envelope.content().actions().get(0), envelope.sender(), envelope.message(), envelope.receivedAt(), envelope.idempotencyKey()));
    }

    private ChannelConnectionMode connectionMode(Object value) {
        String normalized = text(value).toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.isBlank()) return ChannelConnectionMode.LONG_CONNECTION;
        try {
            return ChannelConnectionMode.valueOf(normalized);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("WECOM_CONNECTION_MODE_INVALID:" + normalized, failure);
        }
    }

    private String required(Object value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
