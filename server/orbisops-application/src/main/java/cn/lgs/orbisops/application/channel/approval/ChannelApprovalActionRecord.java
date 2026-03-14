package cn.lgs.orbisops.application.channel.approval;

import java.time.Instant;

public record ChannelApprovalActionRecord(
        String actionId,
        String tokenHash,
        String channelId,
        String projectId,
        String packageId,
        int packageVersion,
        String packageHash,
        Decision decision,
        Status status,
        String issuedBy,
        Instant expiresAt,
        Instant createdAt,
        String consumedBy,
        Instant consumedAt,
        String terminalReason) {

    public ChannelApprovalActionRecord {
        actionId = required(actionId, "CHANNEL_APPROVAL_ACTION_ID_REQUIRED");
        tokenHash = required(tokenHash, "CHANNEL_APPROVAL_TOKEN_HASH_REQUIRED");
        channelId = required(channelId, "CHANNEL_ID_REQUIRED");
        projectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
        packageId = required(packageId, "CHANGE_PACKAGE_ID_REQUIRED");
        if (packageVersion <= 0) throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_INVALID");
        packageHash = required(packageHash, "CHANGE_PACKAGE_HASH_REQUIRED");
        decision = decision == null ? Decision.APPROVE : decision;
        status = status == null ? Status.ACTIVE : status;
        issuedBy = required(issuedBy, "CHANNEL_APPROVAL_ISSUER_REQUIRED");
        expiresAt = expiresAt == null ? Instant.EPOCH : expiresAt;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        consumedBy = text(consumedBy);
        terminalReason = text(terminalReason);
    }

    public boolean expired(Instant now) {
        Instant reference = now == null ? Instant.now() : now;
        return !expiresAt.isAfter(reference);
    }

    public enum Decision {
        APPROVE,
        REJECT
    }

    public enum Status {
        ACTIVE,
        PROCESSING,
        CONSUMED,
        REVOKED
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
