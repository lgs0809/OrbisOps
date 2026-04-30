package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

/** Sanitized, instance-free material produced before experience persistence. */
public record SkillExperiencePolicyResult(
        SkillExperienceTaskTemplate taskTemplate,
        List<String> abstractTrajectory,
        List<SkillExperienceEvidenceReference> evidenceReferences,
        String outcome,
        String finalSummary,
        double qualityScore) {

    public SkillExperiencePolicyResult {
        abstractTrajectory = abstractTrajectory == null
                ? List.of()
                : List.copyOf(abstractTrajectory);
        evidenceReferences = evidenceReferences == null
                ? List.of()
                : List.copyOf(evidenceReferences);
    }
}
