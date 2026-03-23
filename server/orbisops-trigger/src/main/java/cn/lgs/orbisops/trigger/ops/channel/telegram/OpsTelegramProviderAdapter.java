package cn.lgs.orbisops.trigger.ops.channel.telegram;

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
public final class OpsTelegramProviderAdapter implements ChannelProviderAdapter<OpsTelegramChannelConfiguration> {

    private final OpsSecretResolver secrets;
    private final OpsTelegramChannelConnectionDriver driver;

    public OpsTelegramProviderAdapter(OpsSecretResolver secrets, OpsTelegramChannelConnectionDriver driver) {
        if (secrets == null) throw new IllegalArgumentException("CHANNEL_SECRET_RESOLVER_REQUIRED");
        if (driver == null) throw new IllegalArgumentException("TELEGRAM_CONNECTION_DRIVER_REQUIRED");
        this.secrets = secrets;
        this.driver = driver;
    }

    @Override
    public ChannelType type() {
        return ChannelType.TELEGRAM;
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
    public OpsTelegramChannelConfiguration configuration(ChannelRecord channel) {
        if (channel == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (ChannelType.parse(channel.channelType()) != type()) throw new IllegalArgumentException("CHANNEL_PROVIDER_TYPE_MISMATCH");
        Map<String, Object> config = channel.config();
        return new OpsTelegramChannelConfiguration(
                channel.channelId(),
                channel.projectId(),
                channel.credentialRef(),
                connectionMode(config.get("connectionMode")),
                bool(config.get("requireMention"), true),
                text(config.get("botUsername")));
    }

    @Override
    public void validateConfiguration(OpsTelegramChannelConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (!secrets.isReference(configuration.credentialRef())) {
            throw new IllegalArgumentException("TELEGRAM_BOT_TOKEN_MUST_USE_CREDENTIAL_REF");
        }
        if (configuration.connectionMode() != ChannelConnectionMode.LONG_CONNECTION) {
            throw new IllegalArgumentException("TELEGRAM_LONG_POLLING_REQUIRED");
        }
        if (configuration.requireMention() && configuration.botUsername().isBlank()) {
            throw new IllegalArgumentException("TELEGRAM_BOT_USERNAME_REQUIRED_FOR_MENTION_MODE");
        }
    }

    @Override
    public ChannelHealthSnapshot preflight(OpsTelegramChannelConfiguration configuration) {
        validateConfiguration(configuration);
        String credential = secrets.resolve(configuration.credentialRef());
        if (credential == null || credential.isBlank()) {
            return new ChannelHealthSnapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                    "TELEGRAM_CREDENTIAL_UNAVAILABLE",
                    "Telegram bot credential reference is configured but unresolved", Instant.now());
        }
        ChannelHealthSnapshot current = driver.health(configuration.channelId());
        if (current.status() == ChannelHealthSnapshot.HealthStatus.STOPPED) {
            return new ChannelHealthSnapshot(ChannelHealthSnapshot.HealthStatus.UNKNOWN,
                    "TELEGRAM_CONNECTION_NOT_STARTED",
                    "Credential is resolvable; connection supervisor has not established long polling yet", Instant.now());
        }
        return current;
    }

    @Override
    public ChannelInboundEnvelope parseInbound(OpsTelegramChannelConfiguration configuration, ChannelInboundPacket packet) {
        validateConfiguration(configuration);
        throw new UnsupportedOperationException("TELEGRAM_WEBHOOK_NOT_CONFIGURED");
    }

    @Override
    public ChannelDeliveryReceipt send(OpsTelegramChannelConfiguration configuration, ChannelOutboundMessage message) {
        validateConfiguration(configuration);
        return driver.send(configuration, message);
    }

    @Override
    public ChannelDeliveryReceipt updateMessage(OpsTelegramChannelConfiguration configuration,
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
            throw new IllegalArgumentException("TELEGRAM_CONNECTION_MODE_INVALID:" + normalized, failure);
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
