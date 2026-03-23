package cn.lgs.orbisops.trigger.ops.channel.dingtalk;

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
public final class OpsDingTalkProviderAdapter implements ChannelProviderAdapter<OpsDingTalkChannelConfiguration> {

    private final OpsSecretResolver secrets;
    private final OpsDingTalkChannelConnectionDriver driver;

    public OpsDingTalkProviderAdapter(OpsSecretResolver secrets, OpsDingTalkChannelConnectionDriver driver) {
        if (secrets == null) throw new IllegalArgumentException("CHANNEL_SECRET_RESOLVER_REQUIRED");
        if (driver == null) throw new IllegalArgumentException("DINGTALK_CONNECTION_DRIVER_REQUIRED");
        this.secrets = secrets;
        this.driver = driver;
    }

    @Override
    public ChannelType type() {
        return ChannelType.DINGTALK;
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
    public OpsDingTalkChannelConfiguration configuration(ChannelRecord channel) {
        if (channel == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (ChannelType.parse(channel.channelType()) != type()) throw new IllegalArgumentException("CHANNEL_PROVIDER_TYPE_MISMATCH");
        Map<String, Object> config = channel.config();
        return new OpsDingTalkChannelConfiguration(
                channel.channelId(),
                channel.projectId(),
                channel.credentialRef(),
                required(config.get("clientId"), "DINGTALK_CLIENT_ID_REQUIRED"),
                required(config.get("corpId"), "DINGTALK_CORP_ID_REQUIRED"),
                required(config.get("robotCode"), "DINGTALK_ROBOT_CODE_REQUIRED"),
                required(config.get("cardTemplateId"), "DINGTALK_CARD_TEMPLATE_ID_REQUIRED"),
                connectionMode(config.get("connectionMode")));
    }

    @Override
    public void validateConfiguration(OpsDingTalkChannelConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (!secrets.isReference(configuration.credentialRef())) {
            throw new IllegalArgumentException("DINGTALK_CLIENT_SECRET_MUST_USE_CREDENTIAL_REF");
        }
        if (configuration.connectionMode() != ChannelConnectionMode.LONG_CONNECTION) {
            throw new IllegalArgumentException("DINGTALK_STREAM_MODE_REQUIRED");
        }
    }

    @Override
    public ChannelHealthSnapshot preflight(OpsDingTalkChannelConfiguration configuration) {
        validateConfiguration(configuration);
        String clientSecret = secrets.resolve(configuration.credentialRef());
        if (clientSecret == null || clientSecret.isBlank()) {
            return new ChannelHealthSnapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                    "DINGTALK_CLIENT_SECRET_UNAVAILABLE",
                    "Client Secret reference is configured but unresolved", Instant.now());
        }
        ChannelHealthSnapshot current = driver.health(configuration.channelId());
        if (current.status() == ChannelHealthSnapshot.HealthStatus.STOPPED) {
            return new ChannelHealthSnapshot(ChannelHealthSnapshot.HealthStatus.UNKNOWN,
                    "DINGTALK_CONNECTION_NOT_STARTED",
                    "Credentials are resolvable; connection supervisor has not established Stream connection yet", Instant.now());
        }
        return current;
    }

    @Override
    public ChannelInboundEnvelope parseInbound(OpsDingTalkChannelConfiguration configuration, ChannelInboundPacket packet) {
        validateConfiguration(configuration);
        throw new UnsupportedOperationException("DINGTALK_WEBHOOK_FALLBACK_NOT_CONFIGURED");
    }

    @Override
    public ChannelDeliveryReceipt send(OpsDingTalkChannelConfiguration configuration, ChannelOutboundMessage message) {
        validateConfiguration(configuration);
        return driver.send(configuration, message);
    }

    @Override
    public ChannelDeliveryReceipt updateMessage(OpsDingTalkChannelConfiguration configuration,
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
            throw new IllegalArgumentException("DINGTALK_CONNECTION_MODE_INVALID:" + normalized, failure);
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
