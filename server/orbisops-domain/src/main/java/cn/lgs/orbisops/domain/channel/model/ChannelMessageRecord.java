package cn.lgs.orbisops.domain.channel.model;

import java.time.Instant;

public record ChannelMessageRecord(
        String messageId,
        String channelId,
        String projectId,
        String externalMessageId,
        String externalConversationId,
        String senderId,
        String sessionId,
        String runId,
        String direction,
        String status,
        String payloadJson,
        String errorMessage,
        Instant createTime,
        Instant updateTime) {
}
