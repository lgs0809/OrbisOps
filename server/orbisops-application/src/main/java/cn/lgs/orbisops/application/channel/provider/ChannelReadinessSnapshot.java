package cn.lgs.orbisops.application.channel.provider;

import java.time.Instant;
import java.util.List;

public record ChannelReadinessSnapshot(String channelId,
                                       String projectId,
                                       ChannelType type,
                                       boolean ready,
                                       List<ChannelReadinessCheck> checks,
                                       Instant checkedAt) {
    public ChannelReadinessSnapshot {
        if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("CHANNEL_ID_REQUIRED");
        if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("CHANNEL_PROJECT_ID_REQUIRED");
        if (type == null) throw new IllegalArgumentException("CHANNEL_TYPE_REQUIRED");
        checks = checks == null || checks.isEmpty() ? List.of() : List.copyOf(checks);
        ready = !checks.isEmpty() && checks.stream().allMatch(item -> item.status() == CheckStatus.PASS);
        checkedAt = checkedAt == null ? Instant.now() : checkedAt;
    }

    public enum CheckKind {
        CREDENTIALS,
        CONNECTION,
        INBOUND,
        OUTBOUND,
        IDENTITY_ACCESS
    }

    public enum CheckStatus {
        PASS,
        ACTION_REQUIRED,
        BLOCKED_EXTERNAL
    }

    public record ChannelReadinessCheck(CheckKind kind,
                                        CheckStatus status,
                                        String reasonCode,
                                        String detail) {
        public ChannelReadinessCheck {
            if (kind == null) throw new IllegalArgumentException("CHANNEL_READINESS_KIND_REQUIRED");
            status = status == null ? CheckStatus.ACTION_REQUIRED : status;
            reasonCode = reasonCode == null ? "" : reasonCode.trim();
            detail = detail == null ? "" : detail.trim();
        }
    }
}
