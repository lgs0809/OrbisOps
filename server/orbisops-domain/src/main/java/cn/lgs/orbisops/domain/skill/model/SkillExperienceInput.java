package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

/** Runtime facts admitted into the reusable Skill experience policy. */
public record SkillExperienceInput(
        String projectId,
        String agentId,
        String runId,
        String sessionId,
        String observationType,
        String primaryIntent,
        String normalizedUserGoal,
        List<String> toolEvidence,
        List<SkillExperienceEvidenceReference> evidenceReferences,
        boolean completed,
        String finalOutput,
        int eventCount) {

    public SkillExperienceInput {
        toolEvidence = toolEvidence == null ? List.of() : List.copyOf(toolEvidence);
        evidenceReferences = evidenceReferences == null
                ? List.of()
                : List.copyOf(evidenceReferences);
    }
}
