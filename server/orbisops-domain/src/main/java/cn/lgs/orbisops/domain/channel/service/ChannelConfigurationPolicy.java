package cn.lgs.orbisops.domain.channel.service;

import cn.lgs.orbisops.domain.channel.model.ChannelRecord;

/** Domain invariants for Channel transport identity; execution binding validates itself. */
public final class ChannelConfigurationPolicy {

    public ChannelRecord create(ChannelRecord candidate) {
        if (candidate == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        return candidate;
    }

    public ChannelRecord update(ChannelRecord current, ChannelRecord candidate) {
        if (current == null) throw new IllegalArgumentException("CHANNEL_CURRENT_REQUIRED");
        if (candidate == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        immutable(current.channelId(), candidate.channelId(), "channelId");
        immutable(current.projectId(), candidate.projectId(), "projectId");
        immutable(current.channelType(), candidate.channelType(), "type");
        return candidate;
    }

    private void immutable(String expected, String supplied, String key) {
        if (!expected.equals(supplied)) {
            throw new IllegalArgumentException("CHANNEL_IDENTITY_IMMUTABLE:" + key);
        }
    }
}
