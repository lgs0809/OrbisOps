package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobStatus;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionRetryTransition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/** Domain rules for Skill Evolution job identity, queue precedence, bounds and state transitions. */
public class SkillEvolutionJobPolicy {

    /** One eligibility contract shared by scheduling and database lease recovery. */
    public static final java.util.Set<String> DEFERRED_FAILURE_CODES = java.util.Set.of(
            "SKILL_CONTENT_REVIEW_UNAVAILABLE", "SKILL_GROUPING_DEFERRED",
            "SKILL_EVIDENCE_INPUT_DEFERRED", "SKILL_MODEL_TRANSPORT_DEFERRED");
    public static final java.util.Set<String> WAIT_FAILURE_CODES = java.util.Set.of(
            "SKILL_EVOLUTION_MODEL_UNAVAILABLE", "SKILL_EVOLUTION_PROPOSAL_PENDING");
    public static final java.util.Set<String> DURABLE_FAILURE_CODES = java.util.stream.Stream.concat(
            DEFERRED_FAILURE_CODES.stream(), WAIT_FAILURE_CODES.stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private static final int MAX_BATCH_SIZE = 20;
    private static final int MAX_LIST_LIMIT = 500;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(60);

    public String jobId(String runId) {
        String run = required(runId, "SKILL_EVOLUTION_RUN_ID_REQUIRED");
        return "skill-evo-" + md5(run);
    }

    public String triggerReason(String value) {
        String normalized = text(value);
        return normalized.isBlank() ? "RUN_COMPLETED" : normalized;
    }

    public String mergedTriggerReason(String existing, String incoming) {
        String current = triggerReason(existing);
        String candidate = triggerReason(incoming);
        if ("USER_EXPLICIT_REMEMBER".equals(candidate)) return candidate;
        if ("USER_EXPLICIT_REMEMBER".equals(current)) return current;
        return candidate;
    }

    public int batchSize(int value) {
        return Math.max(1, Math.min(value, MAX_BATCH_SIZE));
    }

    public int listLimit(int value) {
        return Math.max(1, Math.min(value, MAX_LIST_LIMIT));
    }

    public int maxAttempts(int value) {
        return Math.max(1, value);
    }

    public int claimedAttempts(int selectedAttempts) {
        return Math.max(0, selectedAttempts) + 1;
    }

    public SkillEvolutionJobStatus terminalStatus(String decision) {
        String normalized = text(decision).toUpperCase(Locale.ROOT);
        if (normalized.startsWith("SKIP")) return SkillEvolutionJobStatus.SKIPPED;
        if ("NO_CHANGE".equals(normalized)) return SkillEvolutionJobStatus.NO_CHANGE;
        return SkillEvolutionJobStatus.COMPLETED;
    }

    public String patchStatus(String decision, String validationStatus) {
        if (text(decision).toUpperCase(Locale.ROOT).startsWith("SKIP")) return "SKIPPED";
        String status = text(validationStatus);
        return status.isBlank() ? "CANDIDATE" : status;
    }

    public SkillEvolutionRetryTransition failureTransition(
            int selectedAttempts,
            int configuredMaxAttempts,
            Instant now) {
        int attempts = claimedAttempts(selectedAttempts);
        boolean exhausted = attempts >= maxAttempts(configuredMaxAttempts);
        Instant current = now == null ? Instant.now() : now;
        return new SkillEvolutionRetryTransition(
                exhausted ? SkillEvolutionJobStatus.FAILED : SkillEvolutionJobStatus.PENDING,
                attempts,
                current.plus(RETRY_DELAY));
    }

    public SkillEvolutionRetryTransition failureTransition(int selectedAttempts, int maxAttempts,
            Instant now, String reason) {
        Instant current = now == null ? Instant.now() : now;
        int attempts = Math.max(0, selectedAttempts);
        String code = text(reason);
        if ("SKILL_EVOLUTION_SOURCE_REVOKED".equals(code))
            return new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.SKIPPED, claimedAttempts(attempts), null);
        if ("SKILL_EVOLUTION_MODEL_UNAVAILABLE".equals(code))
            return new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.PENDING, attempts, current.plusSeconds(60));
        if ("SKILL_EVOLUTION_PROPOSAL_PENDING".equals(code))
            return new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.PENDING, attempts, current.plusSeconds(900));
        if (DEFERRED_FAILURE_CODES.contains(code))
            return new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.PENDING, claimedAttempts(attempts),
                    current.plusSeconds(Math.min(3600L, 60L << Math.min(6, attempts))));
        return failureTransition(attempts, maxAttempts, current);
    }

    /** Deferral/backoff uses all attempts; permanent-failure exhaustion uses its own durable counter. */
    public SkillEvolutionRetryTransition failureTransition(SkillEvolutionJobSnapshot claim, int maxAttempts,
            Instant now, String reason) {
        if (!ordinaryFailure(reason, SkillEvolutionJobStatus.PENDING))
            return failureTransition(claim.attempts(), maxAttempts, now, reason);
        Instant current = now == null ? Instant.now() : now;
        return new SkillEvolutionRetryTransition(
                claim.ordinaryFailures() + 1 >= maxAttempts(maxAttempts)
                        ? SkillEvolutionJobStatus.FAILED : SkillEvolutionJobStatus.PENDING,
                claimedAttempts(claim.attempts()), current.plus(RETRY_DELAY));
    }

    public static boolean ordinaryFailure(String reason, SkillEvolutionJobStatus status) {
        return status != SkillEvolutionJobStatus.SKIPPED
                && !"SKILL_EVOLUTION_SOURCE_REVOKED".equals(reason)
                && !DURABLE_FAILURE_CODES.contains(reason == null ? "" : reason.trim());
    }

    public String candidateDecision(boolean targetSkillPresent) {
        return targetSkillPresent ? "UPDATE_SKILL_CANDIDATE" : "CREATE_SKILL_CANDIDATE";
    }

    private String md5(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("Skill Evolution job hash 计算失败", error);
        }
    }

    private String required(String value, String code) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(code);
        return normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
