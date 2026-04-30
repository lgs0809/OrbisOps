package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionPatchSnapshot;

public record SkillEvolutionJobRunResult(
        String jobId,
        String status,
        SkillEvolutionPatchSnapshot patch,
        String error) {

    public SkillEvolutionJobRunResult {
        jobId = text(jobId);
        status = text(status);
        error = text(error);
    }

    public static SkillEvolutionJobRunResult completed(SkillEvolutionPatchSnapshot patch) {
        if (patch == null) throw new IllegalArgumentException("SKILL_EVOLUTION_PATCH_REQUIRED");
        return new SkillEvolutionJobRunResult(patch.jobId(), patch.status(), patch, "");
    }

    public static SkillEvolutionJobRunResult failed(String jobId, String status, String error) {
        return new SkillEvolutionJobRunResult(jobId, status, null, error);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
