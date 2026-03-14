package cn.lgs.orbisops.application.channel.provider;

import java.util.Set;

public record ChannelProtocolDescriptor(ChannelType type,
                                        String displayName,
                                        boolean supportsInbound,
                                        boolean supportsOutbound,
                                        Set<ChannelConnectionMode> connectionModes,
                                        ChannelCapabilitySet capabilitySet) {
    public ChannelProtocolDescriptor {
        if (type == null) throw new IllegalArgumentException("CHANNEL_TYPE_REQUIRED");
        displayName = displayName == null || displayName.isBlank() ? type.name() : displayName.trim();
        connectionModes = connectionModes == null || connectionModes.isEmpty()
                ? Set.of()
                : Set.copyOf(connectionModes);
        capabilitySet = capabilitySet == null ? new ChannelCapabilitySet(Set.of()) : capabilitySet;
    }
}
