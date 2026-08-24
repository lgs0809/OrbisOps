package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobStatus;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionRetryTransition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
        return failureTransition(selectedAttempts, configuredMaxAttempts, now, "SKILL_EVOLUTION_TECHNICAL_FAILURE");
    }

    public SkillEvolutionRetryTransition failureTransition(int selectedAttempts, int maxAttempts,
            Instant now, String reason) {
        int attempts = claimedAttempts(selectedAttempts);
        Instant current = now == null ? Instant.now() : now;
        if ("SKILL_EVOLUTION_SOURCE_REVOKED".equals(text(reason)))
            return new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.SKIPPED, attempts, null);
        if (sourceBlocked(reason))
            return new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.FAILED, attempts, null);
        return new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.PENDING, attempts,
                current.plusMillis(cn.lgs.orbisops.domain.shared.service.BackgroundModelRetryPolicy.delayMillis(
                        "skill-evolution", attempts, 60_000, "SKILL_EVOLUTION_PROPOSAL_PENDING".equals(reason)?900_000:0)));
    }

    /** Attempt counters remain diagnostic; no model/worker lifetime quota is imposed. */
    public SkillEvolutionRetryTransition failureTransition(SkillEvolutionJobSnapshot claim, int maxAttempts,
            Instant now, String reason) {
        var transition = failureTransition(claim.attempts(), maxAttempts, now, reason);
        if (transition.status() != SkillEvolutionJobStatus.PENDING) return transition;
        Instant current = now == null ? Instant.now() : now;
        return new SkillEvolutionRetryTransition(transition.status(), transition.attempts(),
                current.plusMillis(cn.lgs.orbisops.domain.shared.service.BackgroundModelRetryPolicy.delayMillis(
                        claim.jobId(), transition.attempts(), 60_000, "SKILL_EVOLUTION_PROPOSAL_PENDING".equals(reason)?900_000:0)));
    }

    /** Invalid/revoked source facts are quarantined, never retried until they magically pass. */
    public static boolean sourceBlocked(String reason) {
        String value = reason == null ? "" : reason.trim();
        return value.startsWith("SKILL_ATOMIC_SOURCE_") || value.startsWith("SKILL_EVOLUTION_SOURCE_")
                || value.startsWith("SKILL_SOURCE_REVOKED") || java.util.Set.of("SKILL_EVOLUTION_CLAIM_LOST",
                        "SKILL_GROUPING_METHOD_CHANGED","SKILL_GROUPING_SOURCE_CHANGED","SKILL_GROUPING_OBSERVATION_MISMATCH",
                        "SKILL_EVOLUTION_CANDIDATE_SCOPE_MISMATCH","SKILL_EVOLUTION_CANDIDATE_SOURCE_CHANGED").contains(value);
    }

    public static boolean ordinaryFailure(String reason, SkillEvolutionJobStatus status) {
        return status != SkillEvolutionJobStatus.SKIPPED
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
