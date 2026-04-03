package cn.lgs.orbisops.domain.skill.model;

import java.time.Instant;

/** Retry or configuration-wait transition. Waiting without a model does not consume an attempt. */
public record SkillEvolutionRetryTransition(
        SkillEvolutionJobStatus status,
        int attempts,
        Instant nextRunAt) {

    public SkillEvolutionRetryTransition {
        if (status == null) throw new IllegalArgumentException("SKILL_EVOLUTION_RETRY_STATUS_REQUIRED");
        attempts = Math.max(0, attempts);
    }
}
