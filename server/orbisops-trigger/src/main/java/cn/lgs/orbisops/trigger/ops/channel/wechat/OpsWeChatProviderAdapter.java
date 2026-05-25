package cn.lgs.orbisops.trigger.ops.channel.wechat;

import cn.lgs.orbisops.application.channel.provider.ChannelCapabilitySet;
import cn.lgs.orbisops.application.channel.provider.ChannelCapabilitySet.ChannelCapability;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelHealthSnapshot;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundPacket;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderAdapter;
import cn.lgs.orbisops.application.channel.provider.ChannelType;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Official WeChat Official Account adapter. No personal-account injection or unofficial session bridge is supported. */
@Component
public final class OpsWeChatProviderAdapter implements ChannelProviderAdapter<OpsWeChatChannelConfiguration> {

    private final OpsSecretResolver secrets;
    private final OpsWeChatOfficialAccountClient client;
    private final OpsWeChatProtocolCodec codec = new OpsWeChatProtocolCodec();

    public OpsWeChatProviderAdapter(OpsSecretResolver secrets,
                                    OpsWeChatOfficialAccountClient client) {
        if (secrets == null) throw new IllegalArgumentException("CHANNEL_SECRET_RESOLVER_REQUIRED");
        if (client == null) throw new IllegalArgumentException("WECHAT_API_CLIENT_REQUIRED");
        this.secrets = secrets;
        this.client = client;
    }

    @Override
    public ChannelType type() {
        return ChannelType.WECHAT;
    }

    @Override
    public ChannelCapabilitySet capabilities() {
        return ChannelCapabilitySet.of(
                ChannelCapability.INBOUND,
                ChannelCapability.OUTBOUND,
                ChannelCapability.DIRECT_MESSAGES,
                ChannelCapability.WEBHOOK,
                ChannelCapability.REPLY_TO_INBOUND);
    }

    @Override
    public OpsWeChatChannelConfiguration configuration(ChannelRecord channel) {
        if (channel == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (ChannelType.parse(channel.channelType()) != type()) {
            throw new IllegalArgumentException("CHANNEL_PROVIDER_TYPE_MISMATCH");
        }
        Map<String, Object> config = channel.config();
        return new OpsWeChatChannelConfiguration(
                channel.channelId(),
                channel.projectId(),
                channel.credentialRef(),
                required(config.get("appId"), "WECHAT_APP_ID_REQUIRED"),
                required(config.get("verificationTokenRef"), "WECHAT_VERIFICATION_TOKEN_REF_REQUIRED"),
                required(config.get("encodingAesKeyRef"), "WECHAT_ENCODING_AES_KEY_REF_REQUIRED"),
                connectionMode(config.get("connectionMode")));
    }

    @Override
    public void validateConfiguration(OpsWeChatChannelConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (!secrets.isReference(configuration.credentialRef())) {
            throw new IllegalArgumentException("WECHAT_APP_SECRET_MUST_USE_CREDENTIAL_REF");
        }
        if (!secrets.isReference(configuration.verificationTokenRef())) {
            throw new IllegalArgumentException("WECHAT_VERIFICATION_TOKEN_MUST_USE_CREDENTIAL_REF");
        }
        if (!secrets.isReference(configuration.encodingAesKeyRef())) {
            throw new IllegalArgumentException("WECHAT_ENCODING_AES_KEY_MUST_USE_CREDENTIAL_REF");
        }
        if (configuration.connectionMode() != ChannelConnectionMode.WEBHOOK) {
            throw new IllegalArgumentException("WECHAT_CONNECTION_MODE_UNSUPPORTED:" + configuration.connectionMode());
        }
    }

    @Override
    public ChannelHealthSnapshot preflight(OpsWeChatChannelConfiguration configuration) {
        validateConfiguration(configuration);
        String appSecret = secrets.resolve(configuration.credentialRef());
        if (blank(appSecret)) {
            return blocked("WECHAT_APP_SECRET_UNAVAILABLE", "WeChat App Secret reference is configured but unresolved");
        }
        String verificationToken = secrets.resolve(configuration.verificationTokenRef());
        if (blank(verificationToken)) {
            return blocked("WECHAT_VERIFICATION_TOKEN_UNAVAILABLE", "WeChat callback verification token reference is unresolved");
        }
        String encodingAesKey = secrets.resolve(configuration.encodingAesKeyRef());
        if (blank(encodingAesKey)) {
            return blocked("WECHAT_ENCODING_AES_KEY_UNAVAILABLE", "WeChat EncodingAESKey reference is unresolved");
        }
        try {
            codec.validateEncodingAesKey(encodingAesKey);
            client.token(configuration);
        } catch (RuntimeException failure) {
            return blocked("WECHAT_AUTHENTICATION_FAILED", safe(failure.getMessage()));
        }
        return new ChannelHealthSnapshot(
                ChannelHealthSnapshot.HealthStatus.UNKNOWN,
                "WECHAT_CALLBACK_EXTERNAL_VERIFICATION_REQUIRED",
                "Official Account API credentials are valid; readiness still requires a real secure WeChat callback",
                Instant.now());
    }

    @Override
    public ChannelInboundEnvelope parseInbound(OpsWeChatChannelConfiguration configuration, ChannelInboundPacket packet) {
        return parseCallback(configuration, packet)
                .orElseThrow(() -> new IllegalArgumentException("WECHAT_MESSAGE_TYPE_UNSUPPORTED"));
    }

    public Optional<ChannelInboundEnvelope> parseCallback(OpsWeChatChannelConfiguration configuration, ChannelInboundPacket packet) {
        validateConfiguration(configuration);
        return codec.inbound(
                resolved(configuration.verificationTokenRef(), "WECHAT_VERIFICATION_TOKEN_UNAVAILABLE"),
                resolved(configuration.encodingAesKeyRef(), "WECHAT_ENCODING_AES_KEY_UNAVAILABLE"),
                configuration.appId(),
                packet);
    }

    public String verifyChallenge(OpsWeChatChannelConfiguration configuration,
                           String timestamp,
                           String nonce,
                           String messageSignature,
                           String echoStr) {
        validateConfiguration(configuration);
        return codec.verifyChallenge(
                resolved(configuration.verificationTokenRef(), "WECHAT_VERIFICATION_TOKEN_UNAVAILABLE"),
                resolved(configuration.encodingAesKeyRef(), "WECHAT_ENCODING_AES_KEY_UNAVAILABLE"),
                configuration.appId(), timestamp, nonce, messageSignature, echoStr);
    }

    @Override
    public ChannelDeliveryReceipt send(OpsWeChatChannelConfiguration configuration, ChannelOutboundMessage message) {
        validateConfiguration(configuration);
        return client.send(configuration, message);
    }

    private ChannelConnectionMode connectionMode(Object value) {
        String normalized = text(value).toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.isBlank()) return ChannelConnectionMode.WEBHOOK;
        try {
            return ChannelConnectionMode.valueOf(normalized);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("WECHAT_CONNECTION_MODE_INVALID:" + normalized, failure);
        }
    }

    private String resolved(String reference, String reasonCode) {
        String value = secrets.resolve(reference);
        if (blank(value)) throw new IllegalStateException(reasonCode);
        return value.trim();
    }

    private ChannelHealthSnapshot blocked(String reasonCode, String detail) {
        return new ChannelHealthSnapshot(ChannelHealthSnapshot.HealthStatus.BLOCKED_EXTERNAL,
                reasonCode, detail, Instant.now());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String required(Object value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String safe(Object value) {
        String normalized = text(value);
        return normalized.isBlank() ? "WeChat external authentication failed" : normalized;
    }
}
