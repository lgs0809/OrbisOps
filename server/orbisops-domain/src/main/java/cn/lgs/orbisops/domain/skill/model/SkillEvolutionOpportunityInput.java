package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

/** Domain facts used to classify one reusable Skill Evolution opportunity. */
public record SkillEvolutionOpportunityInput(
        String triggerReason,
        List<String> toolEvidence,
        List<SkillEvolutionEvidenceReference> evidenceReferences,
        boolean completed,
        boolean userNegativeFeedback,
        boolean routingCorrected,
        boolean failedThenRecovered,
        String finalOutput) {

    public SkillEvolutionOpportunityInput {
        triggerReason = text(triggerReason);
        toolEvidence = toolEvidence == null ? List.of() : List.copyOf(toolEvidence);
        evidenceReferences = evidenceReferences == null
                ? List.of()
                : List.copyOf(evidenceReferences);
        finalOutput = text(finalOutput);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
