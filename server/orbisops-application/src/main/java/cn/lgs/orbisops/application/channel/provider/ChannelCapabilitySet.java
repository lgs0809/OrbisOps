package cn.lgs.orbisops.application.channel.provider;

import java.util.Set;

public record ChannelCapabilitySet(Set<ChannelCapability> capabilities) {

    public ChannelCapabilitySet {
        capabilities = capabilities == null || capabilities.isEmpty()
                ? Set.of()
                : Set.copyOf(capabilities);
    }

    public static ChannelCapabilitySet of(ChannelCapability... values) {
        return new ChannelCapabilitySet(values == null || values.length == 0 ? Set.of() : Set.of(values));
    }

    public boolean supports(ChannelCapability capability) {
        return capability != null && capabilities.contains(capability);
    }

    public enum ChannelCapability {
        INBOUND,
        OUTBOUND,
        MESSAGE_UPDATE,
        INTERACTIVE_ACTIONS,
        ATTACHMENTS,
        DIRECT_MESSAGES,
        GROUP_MESSAGES,
        WEBHOOK,
        LONG_CONNECTION,
        PROACTIVE_PUSH,
        REPLY_TO_INBOUND
    }
}
