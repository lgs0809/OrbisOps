package cn.lgs.orbisops.trigger.ops.channel.slack;

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
public final class OpsSlackProviderAdapter implements ChannelProviderAdapter<OpsSlackChannelConfiguration> {

    private final OpsSecretResolver secrets;
    private final OpsSlackChannelConnectionDriver driver;

    public OpsSlackProviderAdapter(OpsSecretResolver secrets, OpsSlackChannelConnectionDriver driver) {
        if (secrets == null) throw new IllegalArgumentException("CHANNEL_SECRET_RESOLVER_REQUIRED");
        if (driver == null) throw new IllegalArgumentException("SLACK_CONNECTION_DRIVER_REQUIRED");
        this.secrets = secrets;
        this.driver = driver;
    }

    @Override
    public ChannelType type() {
        return ChannelType.SLACK;
    }

    @Override
    public ChannelCapabilitySet capabilities() {
        return ChannelCapabilitySet.of(
                ChannelCapability.INBOUND,
                ChannelCapability.OUTBOUND,
                ChannelCapability.MESSAGE_UPDATE,
                ChannelCapability.INTERACTIVE_ACTIONS,
                ChannelCapability.ATTACHMENTS,
                ChannelCapability.DIRECT_MESSAGES,
                ChannelCapability.GROUP_MESSAGES,
                ChannelCapability.LONG_CONNECTION,
                ChannelCapability.PROACTIVE_PUSH,
                ChannelCapability.REPLY_TO_INBOUND);
    }

    @Override
    public OpsSlackChannelConfiguration configuration(ChannelRecord channel) {
        if (channel == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (ChannelType.parse(channel.channelType()) != type()) throw new IllegalArgumentException("CHANNEL_PROVIDER_TYPE_MISMATCH");
        Map<String, Object> config = channel.config();
        return new OpsSlackChannelConfiguration(
                channel.channelId(), channel.projectId(), channel.credentialRef(),
                required(config.get("appCredentialRef"), "SLACK_APP_CREDENTIAL_REF_REQUIRED"),
                connectionMode(config.get("connectionMode")),
                bool(config.get("requireMention"), true));
    }

    @Override
    public void validateConfiguration(OpsSlackChannelConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (!secrets.isReference(configuration.credentialRef())) {
            throw new IllegalArgumentException("SLACK_BOT_TOKEN_MUST_USE_CREDENTIAL_REF");
        }
        if (!secrets.isReference(configuration.appCredentialRef())) {
            throw new IllegalArgumentException("SLACK_APP_TOKEN_MUST_USE_CREDENTIAL_REF");
        }
        if (configuration.connectionMode() != ChannelConnectionMode.LONG_CONNECTION) {
            throw new IllegalArgumentException("SLACK_SOCKET_MODE_REQUIRED");
        }
    }

    @Override
    public ChannelHealthSnapshot preflight(OpsSlackChannelConfiguration configuration) {
        validateConfiguration(configuration);
        String botToken = secrets.resolve(configuration.credentialRef());
        String appToken = secrets.resolve(configuration.appCredentialRef());
        if (blank(botToken) || blank(appToken)) {
            return new ChannelHealthSnapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                    "SLACK_CREDENTIAL_UNAVAILABLE",
                    "Slack bot/app token reference is configured but unresolved", Instant.now());
        }
        ChannelHealthSnapshot current = driver.health(configuration.channelId());
        if (current.status() == ChannelHealthSnapshot.HealthStatus.STOPPED) {
            return new ChannelHealthSnapshot(ChannelHealthSnapshot.HealthStatus.UNKNOWN,
                    "SLACK_CONNECTION_NOT_STARTED",
                    "Credentials are resolvable; connection supervisor has not established Socket Mode yet", Instant.now());
        }
        return current;
    }

    @Override
    public ChannelInboundEnvelope parseInbound(OpsSlackChannelConfiguration configuration, ChannelInboundPacket packet) {
        validateConfiguration(configuration);
        throw new UnsupportedOperationException("SLACK_HTTP_EVENTS_NOT_CONFIGURED");
    }

    @Override
    public ChannelDeliveryReceipt send(OpsSlackChannelConfiguration configuration, ChannelOutboundMessage message) {
        validateConfiguration(configuration);
        return driver.send(configuration, message);
    }

    @Override
    public ChannelDeliveryReceipt updateMessage(OpsSlackChannelConfiguration configuration,
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
            throw new IllegalArgumentException("SLACK_CONNECTION_MODE_INVALID:" + normalized, failure);
        }
    }

    private boolean bool(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        String normalized = text(value);
        return normalized.isBlank() ? fallback : Boolean.parseBoolean(normalized);
    }

    private String required(Object value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
