package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelOutboundDeliveryPort;
import cn.lgs.orbisops.application.channel.provider.ChannelCapabilitySet.ChannelCapability;
import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelMessageRef;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderAdapter;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderConfiguration;
import cn.lgs.orbisops.application.channel.provider.ChannelType;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public final class OpsChannelOutboundDeliveryAdapter implements ChannelOutboundDeliveryPort {

    private final ObjectProvider<ChannelProviderAdapter<?>> adapters;

    public OpsChannelOutboundDeliveryAdapter(ObjectProvider<ChannelProviderAdapter<?>> adapters) {
        if (adapters == null) throw new IllegalArgumentException("CHANNEL_PROVIDER_REGISTRY_REQUIRED");
        this.adapters = adapters;
    }

    @Override
    public ChannelDeliveryReceipt send(ChannelRecord channel, ChannelOutboundMessage message) {
        if (channel == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (message == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_MESSAGE_REQUIRED");
        return sendCaptured(adapter(channel.channelType()), channel, message);
    }

    @Override
    public boolean supportsMessageUpdate(ChannelRecord channel) {
        if (channel == null) return false;
        return adapter(channel.channelType()).capabilities().supports(ChannelCapability.MESSAGE_UPDATE);
    }

    @Override
    public ChannelDeliveryReceipt update(ChannelRecord channel,
                                         ChannelMessageRef existingMessage,
                                         ChannelOutboundMessage message) {
        if (channel == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (existingMessage == null) throw new IllegalArgumentException("CHANNEL_MESSAGE_REF_REQUIRED");
        if (message == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_MESSAGE_REQUIRED");
        return updateCaptured(adapter(channel.channelType()), channel, existingMessage, message);
    }

    private ChannelProviderAdapter<?> adapter(String channelType) {
        ChannelType type = ChannelType.parse(channelType);
        return adapters.orderedStream()
                .filter(candidate -> candidate.type() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("CHANNEL_DELIVERY_ADAPTER_NOT_CONFIGURED:" + type.name()));
    }

    private <C extends ChannelProviderConfiguration> ChannelDeliveryReceipt sendCaptured(ChannelProviderAdapter<C> adapter,
                                                                                          ChannelRecord channel,
                                                                                          ChannelOutboundMessage message) {
        C configuration = adapter.configuration(channel);
        adapter.validateConfiguration(configuration);
        ChannelDeliveryReceipt receipt = adapter.send(configuration, message);
        return receipt == null ? ChannelDeliveryReceipt.skipped("CHANNEL_DELIVERY_RESULT_MISSING") : receipt;
    }

    private <C extends ChannelProviderConfiguration> ChannelDeliveryReceipt updateCaptured(
            ChannelProviderAdapter<C> adapter,
            ChannelRecord channel,
            ChannelMessageRef existingMessage,
            ChannelOutboundMessage message) {
        C configuration = adapter.configuration(channel);
        adapter.validateConfiguration(configuration);
        if (!adapter.capabilities().supports(ChannelCapability.MESSAGE_UPDATE)) {
            throw new UnsupportedOperationException("CHANNEL_MESSAGE_UPDATE_UNSUPPORTED:" + adapter.type().name());
        }
        ChannelDeliveryReceipt receipt = adapter.updateMessage(configuration, existingMessage, message);
        return receipt == null ? ChannelDeliveryReceipt.skipped("CHANNEL_UPDATE_RESULT_MISSING") : receipt;
    }
}
