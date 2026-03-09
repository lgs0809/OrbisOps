package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

/** Compact, non-runtime sample used by batch Skill consolidation. */
public record SkillExperienceConsolidationSample(
        String observationId,
        String runId,
        String sessionId,
        String observationType,
        String outcome,
        String taskTemplateHash,
        String trajectoryHash,
        String summary,
        double qualityScore,
        List<SkillExperienceEvidenceReference> evidenceReferences) {

    public SkillExperienceConsolidationSample {
        evidenceReferences = evidenceReferences == null
                ? List.of()
                : List.copyOf(evidenceReferences);
    }

    public boolean hardCase() {
        return !"SUCCEEDED".equalsIgnoreCase(outcome)
                || List.of(
                        "USER_NEGATIVE_FEEDBACK",
                        "ROUTING_CORRECTION",
                        "FAILED_THEN_RECOVERED_PATTERN")
                .contains(observationType);
    }
}
