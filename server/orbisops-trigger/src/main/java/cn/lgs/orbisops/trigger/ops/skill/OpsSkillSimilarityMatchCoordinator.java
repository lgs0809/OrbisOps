package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;

import java.util.Map;

/** Catalog traversal, frozen guard precedence, threshold admission, and best-match selection. */
final class OpsSkillSimilarityMatchCoordinator {

    private final SkillCatalogQueryService catalogQueryService;
    private final OpsSkillSimilaritySettings settings;
    private final OpsSkillSimilaritySignatureFactory signatureFactory;
    private final OpsSkillSimilarityScorer scorer;
    private final OpsSkillSimilarityMatchProjector projector;
    private final OpsSkillSimilarityTextMetrics metrics;

    OpsSkillSimilarityMatchCoordinator(
            SkillCatalogQueryService catalogQueryService,
            OpsSkillSimilaritySettings settings,
            OpsSkillSemanticMatcher semanticMatcher) {
        this(
                catalogQueryService,
                settings,
                new OpsSkillSimilaritySignatureFactory(),
                new OpsSkillSimilarityScorer(semanticMatcher),
                new OpsSkillSimilarityMatchProjector(),
                new OpsSkillSimilarityTextMetrics());
    }

    OpsSkillSimilarityMatchCoordinator(
            SkillCatalogQueryService catalogQueryService,
            OpsSkillSimilaritySettings settings,
            OpsSkillSimilaritySignatureFactory signatureFactory,
            OpsSkillSimilarityScorer scorer,
            OpsSkillSimilarityMatchProjector projector,
            OpsSkillSimilarityTextMetrics metrics) {
        this.catalogQueryService = catalogQueryService;
        this.settings = settings == null
                ? OpsSkillSimilaritySettings.defaults()
                : settings;
        this.signatureFactory = signatureFactory;
        this.scorer = scorer;
        this.projector = projector;
        this.metrics = metrics;
    }

    Map<String, Object> bestMatch(
            String projectId,
            Map<String, Object> candidate) {
        OpsSkillSimilaritySignatureFactory.CandidateContext candidateContext =
                signatureFactory.candidate(candidate);
        OpsSkillSimilarityMatchProjector.Match bestProject =
                OpsSkillSimilarityMatchProjector.Match.empty();
        OpsSkillSimilarityMatchProjector.Match bestFrozen =
                OpsSkillSimilarityMatchProjector.Match.empty();

        for (Map<String, Object> summary :
                catalogQueryService.listProjectSkills(projectId)) {
            Map<String, Object> skill = catalogQueryService.getProjectSkill(
                    projectId,
                    metrics.text(summary.get("skillId")));
            OpsSkillSimilarityMatchProjector.Match match = match(
                    candidateContext,
                    skill);
            if (projector.isFrozen(skill)
                    && match.score() >= settings.threshold()
                    && match.score() > bestFrozen.score()) {
                bestFrozen = match;
            }
            if (match.score() > bestProject.score()) {
                bestProject = match;
            }
        }

        // A similar frozen global method also blocks creation of a project duplicate,
        // but non-frozen global skills are never mutated by a project Evolver run.
        for (Map<String, Object> summary : catalogQueryService.listGlobalSkills()) {
            if (!projector.isFrozen(summary)) {
                continue;
            }
            Map<String, Object> skill = catalogQueryService.getGlobalSkill(
                    metrics.text(summary.get("skillId")));
            OpsSkillSimilarityMatchProjector.Match match = match(
                    candidateContext,
                    skill);
            if (match.score() >= settings.threshold()
                    && match.score() > bestFrozen.score()) {
                bestFrozen = match;
            }
        }

        if (!bestFrozen.skill().isEmpty()) {
            return projector.view(bestFrozen, "FROZEN_SIMILARITY_GUARD");
        }
        return bestProject.score() >= settings.threshold()
                ? projector.view(bestProject, "BEST_PROJECT_MATCH")
                : Map.of();
    }

    private OpsSkillSimilarityMatchProjector.Match match(
            OpsSkillSimilaritySignatureFactory.CandidateContext candidate,
            Map<String, Object> skill) {
        return new OpsSkillSimilarityMatchProjector.Match(
                skill,
                scorer.score(candidate, signatureFactory.skill(skill)));
    }

}
