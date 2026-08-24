package cn.lgs.orbisops.domain.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobStatus;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionRetryTransition;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionJobPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test void allTechnicalFailuresBackOffWithoutLifetimeQuotaWhileRevokedSourcesStayTerminal() {
        Instant now = Instant.parse("2026-09-28T00:00:00Z");
        for (String reason : SkillEvolutionJobPolicy.DEFERRED_FAILURE_CODES) {
            for (int attempt : new int[]{0, 1, 3, 6, 12}) {
                var result = policy.failureTransition(attempt, 3, now, reason);
                assertEquals(SkillEvolutionJobStatus.PENDING, result.status());
                assertEquals(attempt + 1, result.attempts());
                long delay=java.time.Duration.between(now,result.nextRunAt()).toMillis();
                long base=Math.min(3600000,60000L << Math.min(6,attempt));
                assertTrue(delay>=base && delay<=Math.min(3600000,base+base/10));
            }
        }
        assertEquals(SkillEvolutionJobStatus.PENDING,
                policy.failureTransition(30, 3, now, "SKILL_AUTHORING_RESPONSE_MODEL_MISMATCH").status());
        assertEquals(SkillEvolutionJobStatus.SKIPPED,
                policy.failureTransition(3, 3, now, "SKILL_EVOLUTION_SOURCE_REVOKED").status());
        assertEquals(4, policy.failureTransition(3, 3, now, "SKILL_EVOLUTION_MODEL_UNAVAILABLE").attempts());
        assertEquals(SkillEvolutionJobStatus.FAILED,policy.failureTransition(3,3,now,"SKILL_ATOMIC_SOURCE_NOT_ACTIVE").status());
        for(String reason:java.util.List.of("SKILL_GROUPING_METHOD_CHANGED","SKILL_GROUPING_SOURCE_CHANGED","SKILL_GROUPING_OBSERVATION_MISMATCH","SKILL_EVOLUTION_CANDIDATE_SCOPE_MISMATCH","SKILL_EVOLUTION_CANDIDATE_SOURCE_CHANGED"))
            assertEquals(SkillEvolutionJobStatus.FAILED,policy.failureTransition(30,3,now,reason).status());
        assertEquals(SkillEvolutionJobStatus.PENDING,policy.failureTransition(30,3,now,"SKILL_GROUPING_DECISION_INVALID").status());
    }

    @Test
    void persistsCumulativeTechnicalAttemptsAndIncreasingNextAttempt() {
        Instant now = Instant.parse("2026-07-22T05:00:00Z");

        SkillEvolutionRetryTransition retry = policy.failureTransition(0, 3, now);
        SkillEvolutionRetryTransition failed = policy.failureTransition(2, 3, now);
        SkillEvolutionRetryTransition minimumAttempts = policy.failureTransition(0, 0, now);

        assertAll(
                () -> assertEquals(SkillEvolutionJobStatus.PENDING, retry.status()),
                () -> assertEquals(1, retry.attempts()),
                () -> assertTrue(retry.nextRunAt().isAfter(now.plusSeconds(59))),
                () -> assertEquals(SkillEvolutionJobStatus.PENDING, failed.status()),
                () -> assertEquals(3, failed.attempts()),
                () -> assertTrue(failed.nextRunAt().isAfter(now.plusSeconds(239))),
                () -> assertEquals(SkillEvolutionJobStatus.PENDING, minimumAttempts.status()),
                () -> assertEquals(1, minimumAttempts.attempts()));
    }
}
