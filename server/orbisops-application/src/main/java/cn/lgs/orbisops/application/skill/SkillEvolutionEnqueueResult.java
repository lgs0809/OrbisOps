package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;

public record SkillEvolutionEnqueueResult(
        boolean queued,
        String reason,
        SkillEvolutionJobSnapshot job) {

    public SkillEvolutionEnqueueResult {
        reason = reason == null ? "" : reason.trim();
    }

    public static SkillEvolutionEnqueueResult queued(SkillEvolutionJobSnapshot job) {
        if (job == null) throw new IllegalArgumentException("SKILL_EVOLUTION_JOB_REQUIRED");
        return new SkillEvolutionEnqueueResult(true, "", job);
    }

    public static SkillEvolutionEnqueueResult skipped(String reason) {
        return new SkillEvolutionEnqueueResult(false, reason, null);
    }
}
