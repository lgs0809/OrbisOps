package cn.lgs.orbisops.application.channel.approval;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelApprovalActionTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");

    @Test
    void issueStoresOnlyHashAndActionIsSingleUsePerActorNotGlobally() {
        FakeRepository repository = new FakeRepository();
        ChannelApprovalActionTokenService service = new ChannelApprovalActionTokenService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));

        var issued = service.issue(command(Duration.ofMinutes(15)));
        ChannelApprovalActionRecord stored = repository.records.values().iterator().next();

        assertNotEquals(issued.opaqueActionToken(), stored.tokenHash());
        assertEquals(64, stored.tokenHash().length());
        ChannelApprovalActionRecord resolved = service.resolve(issued.opaqueActionToken());
        assertEquals(ChannelApprovalActionRecord.Status.ACTIVE, resolved.status());

        service.claimForActor(resolved, "user-1");
        SecurityException replay = assertThrows(SecurityException.class,
                () -> service.claimForActor(resolved, "user-1"));
        assertEquals("CHANNEL_APPROVAL_ACTION_REPLAYED_BY_ACTOR", replay.getMessage());

        service.claimForActor(resolved, "user-2");
        assertEquals(ChannelApprovalActionRecord.Status.ACTIVE,
                repository.findByTokenHash(resolved.tokenHash()).orElseThrow().status());
        assertEquals(2, repository.actorClaims.size());
    }

    @Test
    void expiredActionIsRevokedBeforeItCanReachApproval() {
        FakeRepository repository = new FakeRepository();
        ChannelApprovalActionTokenService issuer = new ChannelApprovalActionTokenService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));
        var issued = issuer.issue(command(Duration.ofMinutes(1)));
        ChannelApprovalActionTokenService later = new ChannelApprovalActionTokenService(
                repository, Clock.fixed(NOW.plusSeconds(61), ZoneOffset.UTC));

        SecurityException expired = assertThrows(SecurityException.class,
                () -> later.resolve(issued.opaqueActionToken()));

        assertEquals("CHANNEL_APPROVAL_ACTION_EXPIRED", expired.getMessage());
        assertEquals(ChannelApprovalActionRecord.Status.REVOKED,
                repository.findByTokenHash(later.tokenHash(issued.opaqueActionToken())).orElseThrow().status());
    }

    @Test
    void completionMakesSharedActionTerminalOnlyAfterAuthoritativePackageFinishes() {
        FakeRepository repository = new FakeRepository();
        ChannelApprovalActionTokenService service = new ChannelApprovalActionTokenService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));
        var issued = service.issue(command(Duration.ofMinutes(15)));
        ChannelApprovalActionRecord resolved = service.resolve(issued.opaqueActionToken());
        service.claimForActor(resolved, "user-1");

        service.complete(resolved, "user-1");

        ChannelApprovalActionRecord completed = repository.findByTokenHash(
                service.tokenHash(issued.opaqueActionToken())).orElseThrow();
        assertEquals(ChannelApprovalActionRecord.Status.CONSUMED, completed.status());
        assertEquals("user-1", completed.consumedBy());
        assertTrue(completed.consumedAt() != null);
        SecurityException terminal = assertThrows(SecurityException.class,
                () -> service.resolve(issued.opaqueActionToken()));
        assertEquals("CHANNEL_APPROVAL_ACTION_TERMINAL", terminal.getMessage());
    }

    @Test
    void failedAuthoritativeAttemptCanReleaseActorClaimForSafeRetry() {
        FakeRepository repository = new FakeRepository();
        ChannelApprovalActionTokenService service = new ChannelApprovalActionTokenService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));
        var issued = service.issue(command(Duration.ofMinutes(15)));
        ChannelApprovalActionRecord resolved = service.resolve(issued.opaqueActionToken());

        service.claimForActor(resolved, "user-1");
        service.releaseActorClaim(resolved, "user-1");
        service.claimForActor(resolved, "user-1");

        assertEquals(1, repository.actorClaims.size());
    }

    private ChannelApprovalActionTokenService.IssueCommand command(Duration ttl) {
        return new ChannelApprovalActionTokenService.IssueCommand(
                "channel-1", "project-1", "cp-1", 3, "hash-3",
                ChannelApprovalActionRecord.Decision.APPROVE, "admin", ttl);
    }

    private static final class FakeRepository implements ChannelApprovalActionTokenRepositoryPort {
        private final Map<String, ChannelApprovalActionRecord> records = new LinkedHashMap<>();
        private final Set<String> actorClaims = new HashSet<>();

        @Override
        public void ensureSchema() {
        }

        @Override
        public boolean insert(ChannelApprovalActionRecord record) {
            if (records.containsKey(record.tokenHash())) return false;
            records.put(record.tokenHash(), record);
            return true;
        }

        @Override
        public Optional<ChannelApprovalActionRecord> findByTokenHash(String tokenHash) {
            return Optional.ofNullable(records.get(tokenHash));
        }

        @Override
        public boolean claimForActor(String tokenHash, String actor, Instant claimedAt) {
            ChannelApprovalActionRecord current = records.get(tokenHash);
            if (current == null || current.status() != ChannelApprovalActionRecord.Status.ACTIVE || current.expired(claimedAt)) {
                return false;
            }
            return actorClaims.add(tokenHash + ":" + actor);
        }

        @Override
        public boolean releaseActorClaim(String tokenHash, String actor) {
            return actorClaims.remove(tokenHash + ":" + actor);
        }

        @Override
        public boolean complete(String tokenHash, String actor, Instant consumedAt) {
            ChannelApprovalActionRecord current = records.get(tokenHash);
            if (current == null || current.status() != ChannelApprovalActionRecord.Status.ACTIVE) return false;
            records.put(tokenHash, copy(current, ChannelApprovalActionRecord.Status.CONSUMED, actor, consumedAt, ""));
            return true;
        }

        @Override
        public boolean revoke(String tokenHash, String actor, String reason, Instant revokedAt) {
            ChannelApprovalActionRecord current = records.get(tokenHash);
            if (current == null || current.status() != ChannelApprovalActionRecord.Status.ACTIVE) return false;
            records.put(tokenHash, copy(current, ChannelApprovalActionRecord.Status.REVOKED, actor, revokedAt, reason));
            return true;
        }

        private ChannelApprovalActionRecord copy(ChannelApprovalActionRecord current,
                                                  ChannelApprovalActionRecord.Status status,
                                                  String consumedBy,
                                                  Instant consumedAt,
                                                  String terminalReason) {
            return new ChannelApprovalActionRecord(
                    current.actionId(), current.tokenHash(), current.channelId(), current.projectId(), current.packageId(),
                    current.packageVersion(), current.packageHash(), current.decision(), status, current.issuedBy(),
                    current.expiresAt(), current.createdAt(), consumedBy, consumedAt, terminalReason);
        }
    }
}
