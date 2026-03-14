package cn.lgs.orbisops.application.channel.provider;

import java.time.Instant;

public record ChannelHealthSnapshot(HealthStatus status,
                                    String reasonCode,
                                    String detail,
                                    Instant checkedAt) {
    public ChannelHealthSnapshot {
        status = status == null ? HealthStatus.UNKNOWN : status;
        reasonCode = text(reasonCode);
        detail = text(detail);
        checkedAt = checkedAt == null ? Instant.now() : checkedAt;
    }

    public enum HealthStatus {
        READY,
        DEGRADED,
        BLOCKED_EXTERNAL,
        STOPPED,
        UNKNOWN
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
