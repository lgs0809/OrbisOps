package cn.lgs.orbisops.domain.channel.model;

import java.time.Instant;
import java.time.LocalDateTime;

public record ChannelOutboxRecord(
        Long id,
        String dedupKey,
        String projectId,
        String channelId,
        String target,
        String analysisId,
        String status,
        String messageText,
        String metadataJson,
        String contentHash,
        int retryCount,
        String lastError,
        String lastResponse,
        LocalDateTime nextRetryAt,
        LocalDateTime leaseExpiresAt,
        LocalDateTime deadLetterAt,
        LocalDateTime lastAttemptAt,
        Instant createTime,
        Instant updateTime) {
}
