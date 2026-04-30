package cn.lgs.orbisops.trigger.ops.skill;

/** Weighted lexical, semantic, routing-boundary, section, patch-kind, and intent scoring. */
final class OpsSkillSimilarityScorer {

    private final OpsSkillSemanticMatcher semanticMatcher;
    private final OpsSkillSimilarityTextMetrics metrics;

    OpsSkillSimilarityScorer(OpsSkillSemanticMatcher semanticMatcher) {
        this(semanticMatcher, new OpsSkillSimilarityTextMetrics());
    }

    OpsSkillSimilarityScorer(
            OpsSkillSemanticMatcher semanticMatcher,
            OpsSkillSimilarityTextMetrics metrics) {
        this.semanticMatcher = semanticMatcher;
        this.metrics = metrics;
    }

    double score(
            OpsSkillSimilaritySignatureFactory.CandidateContext candidate,
            OpsSkillSimilaritySignatureFactory.SkillContext skill) {
        if (!"GENERAL".equals(candidate.profile().category())
                && !"GENERAL".equals(skill.profile().category())
                && !candidate.profile().category().equals(skill.profile().category())) {
            return 0D;
        }
        double lexical = metrics.dice(candidate.signature(), skill.signature());
        double semantic = semanticMatcher == null
                ? 0D
                : semanticMatcher.similarity(
                        candidate.signature(),
                        new OpsSkillSemanticMatcher.SkillDocument(
                                skill.skillId(),
                                skill.cacheKey(),
                                skill.signature()));
        double sectionKeys = metrics.jaccard(
                candidate.sectionKeys(),
                skill.sectionKeys());
        double patchKind = metrics.containsIgnoreCase(
                skill.signature(),
                candidate.patchType())
                ? 1D
                : 0D;
        double intent = metrics.dice(
                candidate.reason(),
                skill.description());
        double routing = metrics.dice(
                candidate.profile().positiveText(),
                skill.profile().positiveText());
        double negativeBoundary = metrics.dice(
                candidate.profile().negativeText(),
                skill.profile().negativeText());
        if (semantic > 0D) {
            return lexical * 0.20D
                    + semantic * 0.25D
                    + routing * 0.25D
                    + negativeBoundary * 0.10D
                    + sectionKeys * 0.10D
                    + patchKind * 0.05D
                    + intent * 0.05D;
        }
        return lexical * 0.30D
                + routing * 0.30D
                + negativeBoundary * 0.15D
                + sectionKeys * 0.15D
                + patchKind * 0.05D
                + intent * 0.05D;
    }
}
