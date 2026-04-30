package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionOpportunityInput;

import java.util.Locale;

/** Classifies trusted runtime facts into reusable Skill Evolution opportunities. */
public class SkillEvolutionOpportunityPolicy {

    public String detect(SkillEvolutionOpportunityInput input) {
        if (input == null) return "NO_SIGNAL";
        String trigger = input.triggerReason().toUpperCase(Locale.ROOT);
        boolean trustedEvidence = !input.toolEvidence().isEmpty()
                && !input.evidenceReferences().isEmpty();
        if (trigger.contains("USER_ASSERTED_PROCEDURE")
                || trigger.contains("USER_EXPLICIT_REMEMBER")) {
            return "USER_ASSERTED_PROCEDURE";
        }
        if (input.userNegativeFeedback() && !input.finalOutput().isBlank()) {
            return "USER_NEGATIVE_FEEDBACK";
        }
        if (input.routingCorrected() && trustedEvidence) {
            return "ROUTING_CORRECTION";
        }
        if (input.completed()
                && trustedEvidence
                && input.failedThenRecovered()) {
            return "FAILED_THEN_RECOVERED_PATTERN";
        }
        // Reusability belongs to semantic extraction, not the wording of the report.
        // This signal only schedules a candidate; verified source and publication
        // policy are checked independently by the evolution pipeline.
        if (input.completed() && trustedEvidence) {
            return "SUCCESSFUL_DIAGNOSTIC_PATTERN";
        }
        if (input.completed()
                && (!input.toolEvidence().isEmpty() || !input.finalOutput().isBlank())) {
            return "NO_REUSABLE_PATTERN";
        }
        return "NO_SIGNAL";
    }

}
