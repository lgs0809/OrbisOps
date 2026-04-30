package cn.lgs.orbisops.domain.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobStatus;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionRetryTransition;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionJobPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillEvolutionJobPolicyTest {

    private final SkillEvolutionJobPolicy policy = new SkillEvolutionJobPolicy();

    @Test
    void preservesHistoricalMd5JobIdentity() {
        assertEquals("skill-evo-5b7469680d9900159c653ddb9ff22ae7", policy.jobId(" run-1 "));
        assertThrows(IllegalArgumentException.class, () -> policy.jobId(" "));
    }

    @Test
    void ownsTriggerReasonDefaultAndExplicitRememberPrecedence() {
        assertAll(
                () -> assertEquals("RUN_COMPLETED", policy.triggerReason(null)),
                () -> assertEquals("RUN_COMPLETED", policy.triggerReason(" ")),
                () -> assertEquals("MANUAL_RETRY", policy.triggerReason(" MANUAL_RETRY ")),
                () -> assertEquals("USER_EXPLICIT_REMEMBER",
                        policy.mergedTriggerReason("RUN_COMPLETED", "USER_EXPLICIT_REMEMBER")),
                () -> assertEquals("USER_EXPLICIT_REMEMBER",
                        policy.mergedTriggerReason("USER_EXPLICIT_REMEMBER", "RUN_COMPLETED")),
                () -> assertEquals("MANUAL_RETRY",
                        policy.mergedTriggerReason("RUN_COMPLETED", "MANUAL_RETRY")));
    }

    @Test
    void clampsBatchListAttemptAndClaimBoundaries() {
        assertAll(
                () -> assertEquals(1, policy.batchSize(0)),
                () -> assertEquals(20, policy.batchSize(21)),
                () -> assertEquals(1, policy.listLimit(0)),
                () -> assertEquals(500, policy.listLimit(501)),
                () -> assertEquals(1, policy.maxAttempts(0)),
                () -> assertEquals(1, policy.claimedAttempts(-1)),
                () -> assertEquals(3, policy.claimedAttempts(2)));
    }

    @Test
    void mapsTerminalPatchAndCandidateStates() {
        assertAll(
                () -> assertEquals(SkillEvolutionJobStatus.SKIPPED, policy.terminalStatus("SKIP_UNSAFE")),
                () -> assertEquals(SkillEvolutionJobStatus.NO_CHANGE, policy.terminalStatus("NO_CHANGE")),
                () -> assertEquals(SkillEvolutionJobStatus.COMPLETED,
                        policy.terminalStatus("UPDATE_SKILL_CANDIDATE")),
                () -> assertEquals("SKIPPED", policy.patchStatus("SKIP_LOW_SIGNAL", "APPROVED")),
                () -> assertEquals("CANDIDATE", policy.patchStatus("CREATE_SKILL_CANDIDATE", " ")),
                () -> assertEquals("APPROVED", policy.patchStatus("UPDATE_SKILL_CANDIDATE", " APPROVED ")),
                () -> assertEquals("UPDATE_SKILL_CANDIDATE", policy.candidateDecision(true)),
                () -> assertEquals("CREATE_SKILL_CANDIDATE", policy.candidateDecision(false)));
    }

    @Test void durableFailuresBackOffBeyondTheOrdinaryLimitWhilePermanentFailuresStop() {
        Instant now = Instant.parse("2026-09-28T00:00:00Z");
        for (String reason : SkillEvolutionJobPolicy.DEFERRED_FAILURE_CODES) {
            for (int attempt : new int[]{0, 1, 3, 6, 12}) {
                var result = policy.failureTransition(attempt, 3, now, reason);
                assertEquals(SkillEvolutionJobStatus.PENDING, result.status());
                assertEquals(attempt + 1, result.attempts());
                assertEquals(now.plusSeconds(Math.min(3600, 60L << Math.min(6, attempt))), result.nextRunAt());
            }
        }
        assertEquals(SkillEvolutionJobStatus.FAILED,
                policy.failureTransition(3, 3, now, "SKILL_AUTHORING_RESPONSE_MODEL_MISMATCH").status());
        assertEquals(SkillEvolutionJobStatus.SKIPPED,
                policy.failureTransition(3, 3, now, "SKILL_EVOLUTION_SOURCE_REVOKED").status());
        assertEquals(3, policy.failureTransition(3, 3, now, "SKILL_EVOLUTION_MODEL_UNAVAILABLE").attempts());
    }

    @Test
    void transitionsFailedClaimsToRetryOrTerminalFailureAfterSixtySeconds() {
        Instant now = Instant.parse("2026-07-22T05:00:00Z");

        SkillEvolutionRetryTransition retry = policy.failureTransition(0, 3, now);
        SkillEvolutionRetryTransition failed = policy.failureTransition(2, 3, now);
        SkillEvolutionRetryTransition minimumAttempts = policy.failureTransition(0, 0, now);

        assertAll(
                () -> assertEquals(SkillEvolutionJobStatus.PENDING, retry.status()),
                () -> assertEquals(1, retry.attempts()),
                () -> assertEquals(now.plusSeconds(60), retry.nextRunAt()),
                () -> assertEquals(SkillEvolutionJobStatus.FAILED, failed.status()),
                () -> assertEquals(3, failed.attempts()),
                () -> assertEquals(now.plusSeconds(60), failed.nextRunAt()),
                () -> assertEquals(SkillEvolutionJobStatus.FAILED, minimumAttempts.status()),
                () -> assertEquals(1, minimumAttempts.attempts()));
    }
}
