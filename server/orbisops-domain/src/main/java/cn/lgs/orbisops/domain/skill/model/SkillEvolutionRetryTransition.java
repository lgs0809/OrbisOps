package cn.lgs.orbisops.domain.skill.model;

import java.time.Instant;

/** Retry transition after one claimed Skill Evolution job attempt fails. */
public record SkillEvolutionRetryTransition(
        SkillEvolutionJobStatus status,
        int attempts,
        Instant nextRunAt) {

    public SkillEvolutionRetryTransition {
        if (status == null) throw new IllegalArgumentException("SKILL_EVOLUTION_RETRY_STATUS_REQUIRED");
        attempts = Math.max(1, attempts);
    }
}
