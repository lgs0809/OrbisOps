package cn.lgs.orbisops.application.channel.approval;

import java.time.Instant;
import java.util.Optional;

public interface ChannelApprovalActionTokenRepositoryPort {

    void ensureSchema();

    boolean insert(ChannelApprovalActionRecord record);

    Optional<ChannelApprovalActionRecord> findByTokenHash(String tokenHash);

    /** One action may be used by multiple distinct approvers, but at most once per platform actor. */
    boolean claimForActor(String tokenHash, String actor, Instant claimedAt);

    boolean releaseActorClaim(String tokenHash, String actor);

    boolean complete(String tokenHash, String actor, Instant consumedAt);

    boolean revoke(String tokenHash, String actor, String reason, Instant revokedAt);
}
