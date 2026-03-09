package cn.lgs.orbisops.application.skill;

import java.time.Instant;

public record SkillHiddenEvaluationSuiteSnapshot(
        String suiteId,
        String projectId,
        String skillId,
        long baseVersion,
        String baseSkillHash,
        String suiteVersion,
        String hiddenEvalHash,
        String mutationEvalHash,
        String status,
        String actor,
        Instant createdAt,
        Instant updatedAt
) {

    public SkillHiddenEvaluationSuiteSnapshot {
        suiteId = required(suiteId, "SKILL_HIDDEN_SUITE_ID_REQUIRED");
        projectId = required(projectId, "SKILL_HIDDEN_SUITE_PROJECT_ID_REQUIRED");
        skillId = required(skillId, "SKILL_HIDDEN_SUITE_SKILL_ID_REQUIRED");
        if (baseVersion <= 0) throw new IllegalArgumentException("SKILL_HIDDEN_SUITE_BASE_VERSION_INVALID");
        baseSkillHash = hash(baseSkillHash, "SKILL_HIDDEN_SUITE_BASE_HASH_INVALID");
        suiteVersion = required(suiteVersion, "SKILL_HIDDEN_SUITE_VERSION_REQUIRED");
        hiddenEvalHash = hash(hiddenEvalHash, "SKILL_HIDDEN_EVAL_HASH_INVALID");
        mutationEvalHash = hash(mutationEvalHash, "SKILL_MUTATION_EVAL_HASH_INVALID");
        status = required(status, "SKILL_HIDDEN_SUITE_STATUS_REQUIRED");
        actor = required(actor, "SKILL_HIDDEN_SUITE_ACTOR_REQUIRED");
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("SKILL_HIDDEN_SUITE_TIME_REQUIRED");
        }
    }

    private static String hash(String value, String reasonCode) {
        String normalized = required(value, reasonCode).toLowerCase();
        if (!normalized.matches("[a-f0-9]{64}")) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
