package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Applies Legacy/Shadow/Primary candidate-selection rollout without changing authoring concerns. */
public final class SkillCandidateTournamentRolloutCoordinator {

    private final SkillEvolutionCandidateSetSelector selector;
    private final SkillEvolutionSimilarityPort similarity;
    private final SkillEvolutionPipelineAuditPort audit;
    private final SkillCandidateTournamentSettings settings;

    public SkillCandidateTournamentRolloutCoordinator(
            SkillEvolutionCandidateSetSelector selector,
            SkillEvolutionSimilarityPort similarity,
            SkillEvolutionPipelineAuditPort audit,
            SkillCandidateTournamentSettings settings) {
        if (selector == null || similarity == null || audit == null || settings == null) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_ROLLOUT_DEPENDENCY_REQUIRED");
        }
        this.selector = selector;
        this.similarity = similarity;
        this.audit = audit;
        this.settings = settings;
    }

    public Outcome select(
            SkillEvolutionPipelineRequest request,
            List<SkillEvolutionAuthoredCandidateOption> options,
            SkillCandidateSetSelectionResult legacySelection,
            SkillEvolutionSimilarityMatch legacySimilar) {
        if (request == null || legacySelection == null || legacySimilar == null) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_ROLLOUT_INPUT_REQUIRED");
        }
        SkillCandidateTournamentRolloutMode mode = settings.mode();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("rolloutMode", mode.name());
        details.put("legacySelectionReason", legacySelection.reasonCode());
        details.put("legacyCandidateHash", candidateHash(legacySelection.selectedCandidate()));
        details.put("targetSkillId", legacySimilar.skillId());
        details.put("targetVersion", legacySimilar.version());
        details.put("targetSkillHash", legacySimilar.skillHash());
        if (!mode.strictEnabled()) {
            details.put("status", "LEGACY_ONLY");
            return new Outcome(legacySelection, legacySimilar, "", details);
        }
        if (!strictTargetReady(legacySimilar)) {
            details.put("status", "NOT_APPLICABLE");
            details.put("reasonCode", "SKILL_STRICT_TARGET_REQUIRED");
            record(request, mode, details);
            return mode.strictPrimary()
                    ? new Outcome(
                    legacySelection,
                    legacySimilar,
                    "SKIP_STRICT_TOURNAMENT_TARGET_REQUIRED",
                    details)
                    : new Outcome(legacySelection, legacySimilar, "", details);
        }
        SkillCandidateTournamentContext context = new SkillCandidateTournamentContext(
                "strict-" + request.runId(),
                request.projectId(),
                legacySimilar.skillId(),
                legacySimilar.version(),
                legacySimilar.skillHash(),
                settings.hiddenSuiteVersion(),
                settings.verifierVersion());
        SkillCandidateSetSelectionResult strictSelection;
        try {
            strictSelection = selector.select(
                    SkillCandidateSelectionMode.STRICT_TOURNAMENT,
                    options,
                    context);
        } catch (RuntimeException error) {
            details.put("status", "STRICT_UNAVAILABLE");
            details.put("reasonCode", exceptionReason(error));
            record(request, mode, details);
            return mode.strictPrimary()
                    ? new Outcome(
                    legacySelection,
                    legacySimilar,
                    "SKIP_STRICT_TOURNAMENT_UNAVAILABLE",
                    details)
                    : new Outcome(legacySelection, legacySimilar, "", details);
        }
        details.put("status", "STRICT_EVALUATED");
        details.put("strictSelectionReason", strictSelection.reasonCode());
        details.put("strictCandidateHash", candidateHash(strictSelection.selectedCandidate()));
        details.put("strictTournament", tournamentView(strictSelection));
        details.put("selectionDisagreed", !candidateHash(legacySelection.selectedCandidate())
                .equals(candidateHash(strictSelection.selectedCandidate())));
        if (!mode.strictPrimary()) {
            record(request, mode, details);
            return new Outcome(legacySelection, legacySimilar, "", details);
        }
        if (strictSelection.tournamentResult().manualReviewRequired()) {
            details.put("status", "MANUAL_REVIEW_REQUIRED");
            record(request, mode, details);
            return new Outcome(
                    strictSelection,
                    legacySimilar,
                    "SKIP_STRICT_TOURNAMENT_MANUAL_REVIEW",
                    details);
        }
        if (!strictSelection.selected()) {
            details.put("status", "NO_ELIGIBLE_CANDIDATE");
            record(request, mode, details);
            return new Outcome(
                    strictSelection,
                    legacySimilar,
                    "SKIP_STRICT_TOURNAMENT_NO_ELIGIBLE_CANDIDATE",
                    details);
        }
        SkillEvolutionSimilarityMatch strictSimilar = similarity.bestMatch(
                request.projectId(), strictSelection.selectedCandidate().payload());
        if (strictSimilar == null) strictSimilar = SkillEvolutionSimilarityMatch.none();
        if (!sameTarget(legacySimilar, strictSimilar)) {
            details.put("status", "TARGET_DRIFT");
            details.put("strictTargetSkillId", strictSimilar.skillId());
            details.put("strictTargetVersion", strictSimilar.version());
            details.put("strictTargetSkillHash", strictSimilar.skillHash());
            record(request, mode, details);
            return new Outcome(
                    strictSelection,
                    strictSimilar,
                    "SKIP_STRICT_TOURNAMENT_TARGET_DRIFT",
                    details);
        }
        if (strictSimilar.frozen()) {
            details.put("status", "TARGET_FROZEN");
            record(request, mode, details);
            return new Outcome(
                    strictSelection,
                    strictSimilar,
                    "SKIP_STRICT_TOURNAMENT_TARGET_FROZEN",
                    details);
        }
        details.put("status", "STRICT_PRIMARY_SELECTED");
        record(request, mode, details);
        return new Outcome(strictSelection, strictSimilar, "", details);
    }

    private Map<String, Object> tournamentView(
            SkillCandidateSetSelectionResult selection) {
        if (selection.tournamentResult() == null) return Map.of();
        return Map.of(
                "tournamentId", selection.tournamentResult().tournamentId(),
                "selectedCandidateId", selection.tournamentResult().selectedCandidateId(),
                "manualReviewRequired", selection.tournamentResult().manualReviewRequired(),
                "reasonCodes", selection.tournamentResult().reasonCodes(),
                "verifierVersion", selection.tournamentResult().verifierVersion().compositeId(),
                "rankings", selection.tournamentResult().rankings().stream()
                        .map(this::rankingView)
                        .toList());
    }

    private Map<String, Object> rankingView(
            cn.lgs.orbisops.domain.skill.model.SkillCandidateVerification item) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("candidateId", item.candidateId());
        view.put("candidateHash", item.candidateHash());
        view.put("structuralPassed", item.structural().passed());
        view.put("behaviorAdmitted", item.behavior() != null && item.behavior().admitted());
        view.put("judgeDisposition", item.judge() == null
                ? "NOT_RUN"
                : item.judge().disposition().name());
        view.put("eligible", item.eligible());
        view.put("manualReviewRequired", item.manualReviewRequired());
        view.put("patchComplexity", item.patchComplexity());
        view.put("hiddenEvalHash", item.hiddenEvalHash());
        view.put("mutationEvalHash", item.mutationEvalHash());
        if (item.behavior() != null) {
            view.put("behavior", Map.of(
                    "evaluationId", item.behavior().evaluationId(),
                    "verifierVersion", item.behavior().verifierVersion(),
                    "reasonCodes", item.behavior().reasonCodes(),
                    "arms", Map.of(
                            "NO_SKILL", metricsView(item.behavior().noSkill().metrics()),
                            "BASELINE", metricsView(item.behavior().baseline().metrics()),
                            "CANDIDATE", metricsView(item.behavior().candidate().metrics()))));
        }
        return Map.copyOf(view);
    }

    private Map<String, Object> metricsView(
            cn.lgs.orbisops.domain.skill.model.SkillBehaviorMetrics metrics) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("successRate", metrics.successRate());
        view.put("safetyViolationCount", metrics.safetyViolationCount());
        view.put("toolCallCount", metrics.toolCallCount());
        view.put("invalidToolCallCount", metrics.invalidToolCallCount());
        view.put("evidenceCompleteness", metrics.evidenceCompleteness());
        view.put("hallucinationRate", metrics.hallucinationRate());
        view.put("latencyMs", metrics.latencyMs());
        view.put("tokenCount", metrics.tokenCount());
        view.put("costMicros", metrics.costMicros());
        view.put("finalAnswerQuality", metrics.finalAnswerQuality());
        view.put("routingAccuracy", metrics.routingAccuracy());
        return Map.copyOf(view);
    }

    private void record(
            SkillEvolutionPipelineRequest request,
            SkillCandidateTournamentRolloutMode mode,
            Map<String, Object> details) {
        audit.recordCandidateSelectionCompared(
                request.projectId(),
                request.agentId(),
                request.runId(),
                mode.name(),
                Map.copyOf(details));
    }

    private boolean strictTargetReady(SkillEvolutionSimilarityMatch target) {
        return target.matched()
                && target.version() > 0
                && target.skillHash().matches("[a-fA-F0-9]{64}");
    }

    private boolean sameTarget(
            SkillEvolutionSimilarityMatch expected,
            SkillEvolutionSimilarityMatch actual) {
        return strictTargetReady(expected)
                && strictTargetReady(actual)
                && expected.skillId().equals(actual.skillId())
                && expected.version() == actual.version()
                && expected.skillHash().equalsIgnoreCase(actual.skillHash());
    }

    private String candidateHash(SkillEvolutionAuthoredCandidate candidate) {
        return candidate == null || !candidate.reusableChange()
                ? ""
                : CanonicalObjectHasher.sha256(candidate.payload());
    }

    private String exceptionReason(RuntimeException error) {
        String message = error == null ? "" : String.valueOf(error.getMessage()).trim();
        return message.isBlank() && error != null
                ? error.getClass().getSimpleName()
                : message;
    }

    public record Outcome(
            SkillCandidateSetSelectionResult selection,
            SkillEvolutionSimilarityMatch similar,
            String blockingReason,
            Map<String, Object> details) {

        public Outcome {
            if (selection == null || similar == null) {
                throw new IllegalArgumentException("SKILL_TOURNAMENT_ROLLOUT_RESULT_REQUIRED");
            }
            blockingReason = blockingReason == null ? "" : blockingReason.trim();
            details = details == null ? Map.of() : Map.copyOf(details);
        }
    }
}
