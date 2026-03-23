package cn.lgs.orbisops.trigger.ops.channel.discord;

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
public final class OpsDiscordProviderAdapter implements ChannelProviderAdapter<OpsDiscordChannelConfiguration> {

    private final OpsSecretResolver secrets;
    private final OpsDiscordChannelConnectionDriver driver;

    public OpsDiscordProviderAdapter(OpsSecretResolver secrets, OpsDiscordChannelConnectionDriver driver) {
        if (secrets == null) throw new IllegalArgumentException("CHANNEL_SECRET_RESOLVER_REQUIRED");
        if (driver == null) throw new IllegalArgumentException("DISCORD_CONNECTION_DRIVER_REQUIRED");
        this.secrets = secrets;
        this.driver = driver;
    }

    @Override
    public ChannelType type() {
        return ChannelType.DISCORD;
    }

    @Override
    public ChannelCapabilitySet capabilities() {
        return ChannelCapabilitySet.of(
                ChannelCapability.INBOUND,
                ChannelCapability.OUTBOUND,
                ChannelCapability.MESSAGE_UPDATE,
                ChannelCapability.INTERACTIVE_ACTIONS,
                ChannelCapability.DIRECT_MESSAGES,
                ChannelCapability.GROUP_MESSAGES,
                ChannelCapability.LONG_CONNECTION,
                ChannelCapability.PROACTIVE_PUSH,
                ChannelCapability.REPLY_TO_INBOUND);
    }

    @Override
    public OpsDiscordChannelConfiguration configuration(ChannelRecord channel) {
        if (channel == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (ChannelType.parse(channel.channelType()) != type()) throw new IllegalArgumentException("CHANNEL_PROVIDER_TYPE_MISMATCH");
        Map<String, Object> config = channel.config();
        return new OpsDiscordChannelConfiguration(
                channel.channelId(),
                channel.projectId(),
                channel.credentialRef(),
                connectionMode(config.get("connectionMode")),
                bool(config.get("requireMention"), true),
                bool(config.get("messageContentIntent"), false));
    }

    @Override
    public void validateConfiguration(OpsDiscordChannelConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (!secrets.isReference(configuration.credentialRef())) {
            throw new IllegalArgumentException("DISCORD_BOT_TOKEN_MUST_USE_CREDENTIAL_REF");
        }
        if (configuration.connectionMode() != ChannelConnectionMode.LONG_CONNECTION) {
            throw new IllegalArgumentException("DISCORD_GATEWAY_REQUIRED");
        }
        if (!configuration.requireMention() && !configuration.messageContentIntent()) {
            throw new IllegalArgumentException("DISCORD_MESSAGE_CONTENT_INTENT_REQUIRED_WITHOUT_MENTION_GATE");
        }
    }

    @Override
    public ChannelHealthSnapshot preflight(OpsDiscordChannelConfiguration configuration) {
        validateConfiguration(configuration);
        String credential = secrets.resolve(configuration.credentialRef());
        if (credential == null || credential.isBlank()) {
            return new ChannelHealthSnapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                    "DISCORD_CREDENTIAL_UNAVAILABLE",
                    "Discord bot credential reference is configured but unresolved", Instant.now());
        }
        ChannelHealthSnapshot current = driver.health(configuration.channelId());
        if (current.status() == ChannelHealthSnapshot.HealthStatus.STOPPED) {
            return new ChannelHealthSnapshot(ChannelHealthSnapshot.HealthStatus.UNKNOWN,
                    "DISCORD_CONNECTION_NOT_STARTED",
                    "Credential is resolvable; connection supervisor has not established the Gateway session yet", Instant.now());
        }
        return current;
    }

    @Override
    public ChannelInboundEnvelope parseInbound(OpsDiscordChannelConfiguration configuration, ChannelInboundPacket packet) {
        validateConfiguration(configuration);
        throw new UnsupportedOperationException("DISCORD_HTTP_INTERACTIONS_NOT_CONFIGURED");
    }

    @Override
    public ChannelDeliveryReceipt send(OpsDiscordChannelConfiguration configuration, ChannelOutboundMessage message) {
        validateConfiguration(configuration);
        return driver.send(configuration, message);
    }

    @Override
    public ChannelDeliveryReceipt updateMessage(OpsDiscordChannelConfiguration configuration,
                                                ChannelMessageRef existingMessage,
                                                ChannelOutboundMessage message) {
        validateConfiguration(configuration);
        return driver.update(configuration, existingMessage, message);
    }

    private ChannelConnectionMode connectionMode(Object value) {
        String normalized = text(value).toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.isBlank()) return ChannelConnectionMode.LONG_CONNECTION;
        try {
            return ChannelConnectionMode.valueOf(normalized);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("DISCORD_CONNECTION_MODE_INVALID:" + normalized, failure);
        }
    }

    private boolean bool(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        String normalized = text(value);
        return normalized.isBlank() ? fallback : Boolean.parseBoolean(normalized);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
