package cn.lgs.orbisops.trigger.ops.channel.feishu;

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
public final class OpsFeishuProviderAdapter implements ChannelProviderAdapter<OpsFeishuChannelConfiguration> {

    private final OpsSecretResolver secrets;
    private final OpsFeishuChannelConnectionDriver driver;

    public OpsFeishuProviderAdapter(OpsSecretResolver secrets,
                                    OpsFeishuChannelConnectionDriver driver) {
        if (secrets == null) throw new IllegalArgumentException("CHANNEL_SECRET_RESOLVER_REQUIRED");
        if (driver == null) throw new IllegalArgumentException("FEISHU_CONNECTION_DRIVER_REQUIRED");
        this.secrets = secrets;
        this.driver = driver;
    }

    @Override
    public ChannelType type() {
        return ChannelType.FEISHU;
    }

    @Override
    public ChannelCapabilitySet capabilities() {
        return ChannelCapabilitySet.of(
                ChannelCapability.INBOUND,
                ChannelCapability.OUTBOUND,
                ChannelCapability.MESSAGE_UPDATE,
                ChannelCapability.ATTACHMENTS,
                ChannelCapability.DIRECT_MESSAGES,
                ChannelCapability.GROUP_MESSAGES,
                ChannelCapability.LONG_CONNECTION,
                ChannelCapability.PROACTIVE_PUSH,
                ChannelCapability.REPLY_TO_INBOUND);
    }

    @Override
    public OpsFeishuChannelConfiguration configuration(ChannelRecord channel) {
        if (channel == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (ChannelType.parse(channel.channelType()) != type()) throw new IllegalArgumentException("CHANNEL_PROVIDER_TYPE_MISMATCH");
        Map<String, Object> config = channel.config();
        return new OpsFeishuChannelConfiguration(
                channel.channelId(),
                channel.projectId(),
                channel.credentialRef(),
                required(config.get("appId"), "FEISHU_APP_ID_REQUIRED"),
                connectionMode(config.get("connectionMode")),
                bool(config.get("requireMention"), true),
                bool(config.get("respondToMentionAll"), false),
                text(config.get("verificationTokenRef")),
                text(config.get("encryptKeyRef")));
    }

    @Override
    public void validateConfiguration(OpsFeishuChannelConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (!secrets.isReference(configuration.credentialRef())) {
            throw new IllegalArgumentException("FEISHU_APP_SECRET_MUST_USE_CREDENTIAL_REF");
        }
        if (configuration.connectionMode() != ChannelConnectionMode.LONG_CONNECTION) {
            throw new IllegalArgumentException("FEISHU_CONNECTION_MODE_UNSUPPORTED:" + configuration.connectionMode());
        }
    }

    @Override
    public ChannelHealthSnapshot preflight(OpsFeishuChannelConfiguration configuration) {
        validateConfiguration(configuration);
        String appSecret = secrets.resolve(configuration.credentialRef());
        if (appSecret == null || appSecret.isBlank()) {
            return new ChannelHealthSnapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                    "FEISHU_APP_SECRET_UNAVAILABLE", "Credential reference is configured but unresolved", Instant.now());
        }
        if (configuration.connectionMode() == ChannelConnectionMode.WEBHOOK) {
            return new ChannelHealthSnapshot(ChannelHealthSnapshot.HealthStatus.UNKNOWN,
                    "FEISHU_WEBHOOK_EXTERNAL_VERIFICATION_REQUIRED",
                    "Webhook configuration is valid; inbound verification still requires a real Feishu callback", Instant.now());
        }
        ChannelHealthSnapshot current = driver.health(configuration.channelId());
        if (current.status() == ChannelHealthSnapshot.HealthStatus.STOPPED) {
            return new ChannelHealthSnapshot(ChannelHealthSnapshot.HealthStatus.UNKNOWN,
                    "FEISHU_CONNECTION_NOT_STARTED", "Credentials are resolvable; connection supervisor has not established WebSocket yet", Instant.now());
        }
        return current;
    }

    @Override
    public ChannelInboundEnvelope parseInbound(OpsFeishuChannelConfiguration configuration, ChannelInboundPacket packet) {
        validateConfiguration(configuration);
        throw new UnsupportedOperationException("FEISHU_WEBHOOK_PARSER_NOT_CONFIGURED");
    }

    @Override
    public ChannelDeliveryReceipt send(OpsFeishuChannelConfiguration configuration, ChannelOutboundMessage message) {
        validateConfiguration(configuration);
        if (configuration.connectionMode() == ChannelConnectionMode.WEBHOOK) {
            throw new IllegalStateException("FEISHU_WEBHOOK_OUTBOUND_NOT_CONFIGURED");
        }
        return driver.send(configuration, message);
    }

    @Override
    public ChannelDeliveryReceipt updateMessage(OpsFeishuChannelConfiguration configuration,
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
            throw new IllegalArgumentException("FEISHU_CONNECTION_MODE_INVALID:" + normalized, failure);
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

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
