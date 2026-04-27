package cn.lgs.orbisops.domain.channel.model;

import java.time.Instant;

public record ChannelIdentityRecord(
        String mappingId,
        String channelId,
        String projectId,
        String externalSenderId,
        String platformUserId,
        String username,
        ChannelStatus status,
        long version,
        String createdBy,
        Instant createTime,
        Instant updateTime) {

    public ChannelIdentityRecord {
        mappingId = required(mappingId, "CHANNEL_IDENTITY_MAPPING_ID_REQUIRED");
        channelId = required(channelId, "CHANNEL_ID_REQUIRED");
        projectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
        externalSenderId = required(externalSenderId, "CHANNEL_EXTERNAL_IDENTITY_REQUIRED");
        platformUserId = required(platformUserId, "CHANNEL_PLATFORM_USER_ID_REQUIRED");
        username = username == null ? "" : username.trim();
        status = status == null ? ChannelStatus.ACTIVE : status;
        if (version <= 0) throw new IllegalArgumentException("CHANNEL_IDENTITY_VERSION_INVALID");
        createdBy = required(createdBy, "CHANNEL_ACTOR_REQUIRED");
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
