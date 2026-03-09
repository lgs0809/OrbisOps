package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillBehaviorMetrics;
import cn.lgs.orbisops.domain.skill.model.SkillCandidateTournamentResult;
import cn.lgs.orbisops.domain.skill.model.SkillCandidateVerification;
import cn.lgs.orbisops.domain.skill.model.SkillVerifierVersion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Stable tournament ranking. Deterministic failures are never judge-overridable. */
public final class SkillCandidateSelectionPolicy {

    public SkillCandidateTournamentResult select(
            String tournamentId,
            List<SkillCandidateVerification> candidates,
            SkillVerifierVersion verifierVersion) {
        List<SkillCandidateVerification> safe = candidates == null ? List.of() : candidates;
        List<SkillCandidateVerification> rankings = safe.stream()
                .sorted(ranking())
                .toList();
        String selected = rankings.stream()
                .filter(SkillCandidateVerification::eligible)
                .map(SkillCandidateVerification::candidateId)
                .findFirst()
                .orElse("");
        boolean manual = selected.isBlank()
                && rankings.stream().anyMatch(SkillCandidateVerification::manualReviewRequired);
        List<String> reasons = new ArrayList<>();
        if (selected.isBlank()) {
            reasons.add(manual
                    ? "SKILL_TOURNAMENT_MANUAL_REVIEW_REQUIRED"
                    : "SKILL_TOURNAMENT_NO_ELIGIBLE_CANDIDATE");
        }
        return new SkillCandidateTournamentResult(
                tournamentId, selected, rankings, manual, reasons, verifierVersion);
    }

    private Comparator<SkillCandidateVerification> ranking() {
        return Comparator
                .comparing((SkillCandidateVerification item) -> !item.eligible())
                .thenComparingInt(item -> metrics(item).safetyViolationCount())
                .thenComparing(Comparator.comparingDouble(
                        (SkillCandidateVerification item) -> item.structural().score()).reversed())
                .thenComparing(Comparator.comparingDouble(
                        (SkillCandidateVerification item) -> metrics(item).successRate()).reversed())
                .thenComparing(Comparator.comparingDouble(
                        (SkillCandidateVerification item) -> metrics(item).routingAccuracy()).reversed())
                .thenComparing(Comparator.comparingDouble(
                        (SkillCandidateVerification item) -> metrics(item).evidenceCompleteness()).reversed())
                .thenComparingInt(item -> metrics(item).invalidToolCallCount())
                .thenComparingInt(item -> metrics(item).toolCallCount())
                .thenComparingLong(item -> metrics(item).costMicros())
                .thenComparingLong(item -> metrics(item).latencyMs())
                .thenComparingLong(item -> metrics(item).tokenCount())
                .thenComparingInt(SkillCandidateVerification::patchComplexity)
                .thenComparing(SkillCandidateVerification::candidateId);
    }

    private SkillBehaviorMetrics metrics(SkillCandidateVerification candidate) {
        if (candidate.behavior() == null) {
            return new SkillBehaviorMetrics(
                    0D, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                    0D, 1D, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
                    0D, 0D);
        }
        return candidate.behavior().candidate().metrics();
    }
}
