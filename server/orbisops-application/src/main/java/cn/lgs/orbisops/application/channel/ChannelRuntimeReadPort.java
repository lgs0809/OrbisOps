package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;

import java.util.List;
import java.util.Optional;

/** Typed read boundary used by Channel orchestration without exposing persistence repositories to Trigger. */
public interface ChannelRuntimeReadPort {

    Optional<ChannelRecord> findById(String channelId);

    List<ChannelRecord> findByProject(String projectId);

    Optional<ChannelIdentityRecord> findIdentity(String channelId, String externalUserId);

    List<ChannelIdentityRecord> findIdentities(String projectId, String channelId);

    List<ChannelMessageRecord> findMessages(String projectId, String channelId, int limit);
}
