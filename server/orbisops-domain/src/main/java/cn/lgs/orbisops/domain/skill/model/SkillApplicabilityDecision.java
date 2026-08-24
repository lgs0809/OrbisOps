package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

/** Descriptive relevance only. This decision cannot grant resource or tool permissions. */
public record SkillApplicabilityDecision(Verdict verdict, List<Integer> useCaseIndexes, String evidenceQuote) {
    public enum Verdict { MATCH, NEED_INFO, NO_MATCH }
    public SkillApplicabilityDecision {
        useCaseIndexes = useCaseIndexes == null ? List.of() : List.copyOf(useCaseIndexes);
        evidenceQuote = evidenceQuote == null ? "" : evidenceQuote;
    }
}
