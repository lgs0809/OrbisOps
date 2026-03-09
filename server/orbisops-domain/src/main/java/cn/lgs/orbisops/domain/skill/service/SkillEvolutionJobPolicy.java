package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobStatus;
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
