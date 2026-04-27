package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelRuntimeReadPort;
import cn.lgs.orbisops.application.channel.ReceiveChannelMessageUseCase;
import cn.lgs.orbisops.application.channel.provider.ChannelInboundPacket;
import cn.lgs.orbisops.application.channel.provider.ChannelType;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.trigger.ops.channel.wechat.OpsWeChatChannelConfiguration;
import cn.lgs.orbisops.trigger.ops.channel.wechat.OpsWeChatProviderAdapter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Native WeChat Official Account callback ingress. Provider crypto is verified before the shared durable Channel pipeline. */
@Service
public final class OpsWeChatWebhookService {

    private final ChannelRuntimeReadPort channels;
    private final OpsWeChatProviderAdapter adapter;
    private final ReceiveChannelMessageUseCase inbound;

    public OpsWeChatWebhookService(ChannelRuntimeReadPort channels,
                                   OpsWeChatProviderAdapter adapter,
                                   ReceiveChannelMessageUseCase inbound) {
        if (channels == null) throw new IllegalArgumentException("CHANNEL_RUNTIME_READ_PORT_REQUIRED");
        if (adapter == null) throw new IllegalArgumentException("WECHAT_PROVIDER_ADAPTER_REQUIRED");
        if (inbound == null) throw new IllegalArgumentException("CHANNEL_INBOUND_USE_CASE_REQUIRED");
        this.channels = channels;
        this.adapter = adapter;
        this.inbound = inbound;
    }

    public String verify(String channelId,
                         String timestamp,
                         String nonce,
                         String messageSignature,
                         String echoStr) {
        ChannelRecord channel = wechat(channelId);
        OpsWeChatChannelConfiguration configuration = adapter.configuration(channel);
        return adapter.verifyChallenge(configuration, timestamp, nonce, messageSignature, echoStr);
    }

    public Map<String, Object> receive(String channelId,
                                       String timestamp,
                                       String nonce,
                                       String messageSignature,
                                       byte[] body) {
        ChannelRecord channel = wechat(channelId);
        OpsWeChatChannelConfiguration configuration = adapter.configuration(channel);
        ChannelInboundPacket packet = new ChannelInboundPacket(
                "application/xml",
                body,
                List.of(
                        new ChannelInboundPacket.TransportHeader("X-WeChat-Timestamp", timestamp),
                        new ChannelInboundPacket.TransportHeader("X-WeChat-Nonce", nonce),
                        new ChannelInboundPacket.TransportHeader("X-WeChat-Msg-Signature", messageSignature)));
        var envelope = adapter.parseCallback(configuration, packet);
        if (envelope.isEmpty()) {
            return Map.of("status", "IGNORED_UNSUPPORTED_MESSAGE_TYPE", "channelId", channel.channelId());
        }
        if (channel.status() != ChannelStatus.ACTIVE) {
            return Map.of("status", "IGNORED_CHANNEL_DISABLED", "channelId", channel.channelId());
        }
        if (!channel.acceptsInbound()) {
            return Map.of("status", "IGNORED_INBOUND_DISABLED", "channelId", channel.channelId());
        }
        return inbound.receiveProvider(channel.channelId(), envelope.get());
    }

    private ChannelRecord wechat(String channelId) {
        String safeChannelId = required(channelId, "CHANNEL_ID_REQUIRED");
        ChannelRecord channel = channels.findById(safeChannelId)
                .orElseThrow(() -> new IllegalArgumentException("CHANNEL_NOT_FOUND"));
        if (ChannelType.parse(channel.channelType()) != ChannelType.WECHAT) {
            throw new SecurityException("WECHAT_CHANNEL_TYPE_MISMATCH");
        }
        return channel;
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
