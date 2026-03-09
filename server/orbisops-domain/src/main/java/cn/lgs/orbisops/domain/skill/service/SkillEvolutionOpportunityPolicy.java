package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionOpportunityInput;

import java.util.List;
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
        if (input.userNegativeFeedback() && hasReusableText(input.finalOutput())) {
            return "USER_NEGATIVE_FEEDBACK";
        }
        if (input.routingCorrected() && trustedEvidence) {
            return "ROUTING_CORRECTION";
        }
        if (input.completed()
                && trustedEvidence
                && input.failedThenRecovered()
                && hasReusableText(input.finalOutput())) {
            return "FAILED_THEN_RECOVERED_PATTERN";
        }
        if (input.completed()
                && trustedEvidence
                && hasReusableText(input.finalOutput())) {
            return "SUCCESSFUL_DIAGNOSTIC_PATTERN";
        }
        if (input.completed()
                && (!input.toolEvidence().isEmpty() || !input.finalOutput().isBlank())) {
            return "NO_REUSABLE_PATTERN";
        }
        return "NO_SIGNAL";
    }

    private boolean hasReusableText(String value) {
        if (value == null || value.isBlank() || value.length() < 80) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        return List.of(
                        "因为",
                        "根因",
                        "证据",
                        "步骤",
                        "先",
                        "然后",
                        "验证",
                        "修复",
                        "回滚",
                        "cause",
                        "evidence",
                        "step",
                        "verify",
                        "rollback")
                .stream()
                .anyMatch(normalized::contains);
    }
}
