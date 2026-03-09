package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

/** Domain summary derived from neutral Skill Evolution input. */
public record SkillEvolutionInputSummary(
        List<String> eventSummaries,
        List<String> toolEvents,
        String normalizedUserGoal,
        String finalReport,
        boolean hasCompleted,
        boolean hasToolEvidence,
        boolean hasMessages,
        List<SkillEvolutionEvidenceReference> evidenceReferences,
        String contextBundleHash) {

    public SkillEvolutionInputSummary {
        eventSummaries = eventSummaries == null ? List.of() : List.copyOf(eventSummaries);
        toolEvents = toolEvents == null ? List.of() : List.copyOf(toolEvents);
        normalizedUserGoal = text(normalizedUserGoal);
        finalReport = text(finalReport);
        evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
        contextBundleHash = text(contextBundleHash);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
