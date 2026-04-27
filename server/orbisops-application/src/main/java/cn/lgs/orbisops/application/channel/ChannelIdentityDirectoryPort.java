package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;

public interface ChannelIdentityDirectoryPort {

    PlatformIdentity resolveActive(String userId, String username);

    boolean canAccessProject(String projectId, PlatformIdentity identity);

    boolean isTrusted(String projectId, ChannelIdentityRecord identity);

    record PlatformIdentity(String userId, String username) {
        public PlatformIdentity {
            userId = userId == null ? "" : userId.trim();
            username = username == null ? "" : username.trim();
            if (userId.isBlank() || username.isBlank()) {
                throw new IllegalArgumentException("CHANNEL_PLATFORM_IDENTITY_INVALID");
            }
        }
    }
}
