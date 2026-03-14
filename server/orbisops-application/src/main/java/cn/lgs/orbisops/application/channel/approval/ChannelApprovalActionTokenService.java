package cn.lgs.orbisops.application.channel.approval;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

public final class ChannelApprovalActionTokenService {

    private final ChannelApprovalActionTokenRepositoryPort repository;
    private final Clock clock;

    public ChannelApprovalActionTokenService(ChannelApprovalActionTokenRepositoryPort repository) {
        this(repository, Clock.systemUTC());
    }

    ChannelApprovalActionTokenService(ChannelApprovalActionTokenRepositoryPort repository, Clock clock) {
        if (repository == null) throw new IllegalArgumentException("CHANNEL_APPROVAL_TOKEN_REPOSITORY_REQUIRED");
        if (clock == null) throw new IllegalArgumentException("CHANNEL_APPROVAL_CLOCK_REQUIRED");
        this.repository = repository;
        this.clock = clock;
    }

    public IssuedAction issue(IssueCommand command) {
        if (command == null) throw new IllegalArgumentException("CHANNEL_APPROVAL_ISSUE_COMMAND_REQUIRED");
        Duration ttl = command.ttl() == null ? Duration.ofMinutes(15) : command.ttl();
        if (ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofHours(2)) > 0) {
            throw new IllegalArgumentException("CHANNEL_APPROVAL_TOKEN_TTL_INVALID");
        }
        String opaqueToken = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        String tokenHash = hash(opaqueToken);
        Instant now = clock.instant();
        ChannelApprovalActionRecord record = new ChannelApprovalActionRecord(
                "channel-approval-" + UUID.randomUUID(),
                tokenHash,
                command.channelId(),
                command.projectId(),
                command.packageId(),
                command.packageVersion(),
                command.packageHash(),
                command.decision(),
                ChannelApprovalActionRecord.Status.ACTIVE,
                command.issuedBy(),
                now.plus(ttl),
                now,
                "",
                null,
                "");
        if (!repository.insert(record)) throw new IllegalStateException("CHANNEL_APPROVAL_TOKEN_INSERT_CONFLICT");
        return new IssuedAction(record.actionId(), opaqueToken, record.expiresAt(), record.decision());
    }

    public ChannelApprovalActionRecord resolve(String opaqueToken) {
        String tokenHash = hashRequired(opaqueToken);
        ChannelApprovalActionRecord current = repository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new SecurityException("CHANNEL_APPROVAL_ACTION_INVALID"));
        Instant now = clock.instant();
        if (current.expired(now)) {
            repository.revoke(tokenHash, "system", "EXPIRED", now);
            throw new SecurityException("CHANNEL_APPROVAL_ACTION_EXPIRED");
        }
        if (current.status() != ChannelApprovalActionRecord.Status.ACTIVE) {
            throw new SecurityException("CHANNEL_APPROVAL_ACTION_TERMINAL");
        }
        return current;
    }

    public void claimForActor(ChannelApprovalActionRecord record, String actor) {
        if (record == null) throw new IllegalArgumentException("CHANNEL_APPROVAL_ACTION_REQUIRED");
        String safeActor = required(actor, "CHANNEL_APPROVAL_ACTOR_REQUIRED");
        if (!repository.claimForActor(record.tokenHash(), safeActor, clock.instant())) {
            throw new SecurityException("CHANNEL_APPROVAL_ACTION_REPLAYED_BY_ACTOR");
        }
    }

    public void releaseActorClaim(ChannelApprovalActionRecord record, String actor) {
        if (record == null) return;
        String safeActor = text(actor);
        if (safeActor.isBlank()) return;
        repository.releaseActorClaim(record.tokenHash(), safeActor);
    }

    public void complete(ChannelApprovalActionRecord record, String actor) {
        if (record == null) throw new IllegalArgumentException("CHANNEL_APPROVAL_ACTION_REQUIRED");
        if (!repository.complete(record.tokenHash(), required(actor, "CHANNEL_APPROVAL_ACTOR_REQUIRED"), clock.instant())) {
            throw new IllegalStateException("CHANNEL_APPROVAL_ACTION_COMPLETION_CONFLICT");
        }
    }

    public void revoke(ChannelApprovalActionRecord record, String actor, String reason) {
        if (record == null) return;
        repository.revoke(record.tokenHash(), text(actor), text(reason), clock.instant());
    }

    public java.util.Optional<ChannelApprovalActionRecord> find(String opaqueToken) {
        String tokenHash = hashRequired(opaqueToken);
        return repository.findByTokenHash(tokenHash);
    }

    public String tokenHash(String opaqueToken) {
        return hashRequired(opaqueToken);
    }

    private String hashRequired(String opaqueToken) {
        String normalized = required(opaqueToken, "CHANNEL_APPROVAL_ACTION_TOKEN_REQUIRED");
        if (normalized.length() < 32 || normalized.length() > 256) {
            throw new SecurityException("CHANNEL_APPROVAL_ACTION_TOKEN_INVALID");
        }
        return hash(normalized);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("CHANNEL_APPROVAL_HASH_UNAVAILABLE", failure);
        }
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    public record IssueCommand(String channelId,
                               String projectId,
                               String packageId,
                               int packageVersion,
                               String packageHash,
                               ChannelApprovalActionRecord.Decision decision,
                               String issuedBy,
                               Duration ttl) {
        public IssueCommand {
            channelId = required(channelId, "CHANNEL_ID_REQUIRED");
            projectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
            packageId = required(packageId, "CHANGE_PACKAGE_ID_REQUIRED");
            if (packageVersion <= 0) throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_INVALID");
            packageHash = required(packageHash, "CHANGE_PACKAGE_HASH_REQUIRED");
            decision = decision == null ? ChannelApprovalActionRecord.Decision.APPROVE : decision;
            issuedBy = required(issuedBy, "CHANNEL_APPROVAL_ISSUER_REQUIRED");
        }
    }

    public record IssuedAction(String actionId,
                               String opaqueActionToken,
                               Instant expiresAt,
                               ChannelApprovalActionRecord.Decision decision) {
        public IssuedAction {
            actionId = required(actionId, "CHANNEL_APPROVAL_ACTION_ID_REQUIRED");
            opaqueActionToken = required(opaqueActionToken, "CHANNEL_APPROVAL_ACTION_TOKEN_REQUIRED");
            if (expiresAt == null) throw new IllegalArgumentException("CHANNEL_APPROVAL_ACTION_EXPIRY_REQUIRED");
            decision = decision == null ? ChannelApprovalActionRecord.Decision.APPROVE : decision;
        }
    }
}
