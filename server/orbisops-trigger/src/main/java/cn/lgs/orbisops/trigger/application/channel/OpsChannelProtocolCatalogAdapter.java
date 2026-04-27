package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelProtocolCatalogPort;
import cn.lgs.orbisops.application.channel.provider.ChannelCapabilitySet.ChannelCapability;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelProtocolDescriptor;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderAdapter;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderConfiguration;
import cn.lgs.orbisops.application.channel.provider.ChannelType;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public final class OpsChannelProtocolCatalogAdapter implements ChannelProtocolCatalogPort {

    private final ObjectProvider<ChannelProviderAdapter<?>> adapters;

    public OpsChannelProtocolCatalogAdapter(ObjectProvider<ChannelProviderAdapter<?>> adapters) {
        if (adapters == null) throw new IllegalArgumentException("CHANNEL_PROVIDER_REGISTRY_REQUIRED");
        this.adapters = adapters;
    }

    @Override
    public List<ChannelProtocolDescriptor> supportedTypes() {
        return adapters.orderedStream()
                .sorted((left, right) -> left.type().name().compareTo(right.type().name()))
                .map(this::descriptor)
                .toList();
    }

    @Override
    public void validateConfiguration(ChannelRecord channel) {
        if (channel == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        validateCaptured(adapter(channel.channelType()), channel);
    }

    private ChannelProtocolDescriptor descriptor(ChannelProviderAdapter<?> adapter) {
        boolean inbound = adapter.capabilities().supports(ChannelCapability.INBOUND);
        boolean outbound = adapter.capabilities().supports(ChannelCapability.OUTBOUND);
        Set<ChannelConnectionMode> connectionModes = adapter.capabilities().supports(ChannelCapability.LONG_CONNECTION)
                ? adapter.capabilities().supports(ChannelCapability.WEBHOOK)
                ? Set.of(ChannelConnectionMode.LONG_CONNECTION, ChannelConnectionMode.WEBHOOK, ChannelConnectionMode.HYBRID)
                : Set.of(ChannelConnectionMode.LONG_CONNECTION)
                : adapter.capabilities().supports(ChannelCapability.WEBHOOK)
                ? Set.of(ChannelConnectionMode.WEBHOOK)
                : Set.of();
        return new ChannelProtocolDescriptor(adapter.type(), displayName(adapter.type()), inbound, outbound,
                connectionModes, adapter.capabilities());
    }

    private String displayName(ChannelType type) {
        return switch (type) {
            case GENERIC_WEBHOOK -> "Generic Webhook";
            case FEISHU -> "Feishu / Lark";
            case WECOM -> "WeCom";
            case DINGTALK -> "DingTalk";
            case SLACK -> "Slack";
            case TELEGRAM -> "Telegram";
            case DISCORD -> "Discord";
            case QQ -> "QQ";
            case WECHAT -> "WeChat";
        };
    }

    private ChannelProviderAdapter<?> adapter(String channelType) {
        ChannelType type = ChannelType.parse(channelType);
        return adapters.orderedStream()
                .filter(candidate -> candidate.type() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("CHANNEL_PROVIDER_NOT_CONFIGURED:" + type.name()));
    }

    private <C extends ChannelProviderConfiguration> void validateCaptured(ChannelProviderAdapter<C> adapter,
                                                                           ChannelRecord channel) {
        C configuration = adapter.configuration(channel);
        adapter.validateConfiguration(configuration);
    }
}
