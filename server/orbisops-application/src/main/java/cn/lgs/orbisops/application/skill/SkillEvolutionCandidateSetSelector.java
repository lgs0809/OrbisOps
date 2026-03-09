package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillCandidateTournamentResult;
import cn.lgs.orbisops.domain.skill.model.SkillVerifierVersion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit dual-track migration boundary for legacy and strict candidate selection. */
public final class SkillEvolutionCandidateSetSelector {

    private final SkillCandidateTournament strictTournament;

    public SkillEvolutionCandidateSetSelector() {
        this(null);
    }

    public SkillEvolutionCandidateSetSelector(
            SkillCandidateTournament strictTournament) {
        this.strictTournament = strictTournament;
    }

    public SkillCandidateSetSelectionResult select(
            SkillCandidateSelectionMode mode,
            List<SkillEvolutionAuthoredCandidateOption> options,
            String tournamentId,
            String skillId,
            long baseVersion,
            SkillVerifierVersion verifierVersion) {
        if (mode == SkillCandidateSelectionMode.LEGACY_COMPATIBILITY) {
            return select(mode, options, null);
        }
        return select(mode, options, new SkillCandidateTournamentContext(
                tournamentId,
                "legacy",
                skillId,
                baseVersion,
                CanonicalObjectHasher.sha256(Map.of(
                        "skillId", skillId,
                        "baseVersion", baseVersion)),
                "legacy",
                verifierVersion));
    }

    public SkillCandidateSetSelectionResult select(
            SkillCandidateSelectionMode mode,
            List<SkillEvolutionAuthoredCandidateOption> options,
            SkillCandidateTournamentContext context) {
        if (mode == null) throw new IllegalArgumentException("SKILL_SELECTION_MODE_REQUIRED");
        List<SkillEvolutionAuthoredCandidateOption> safe = options == null
                ? List.of() : List.copyOf(options);
        return switch (mode) {
            case LEGACY_COMPATIBILITY -> legacy(safe);
            case STRICT_TOURNAMENT -> strict(safe, context);
        };
    }

    private SkillCandidateSetSelectionResult legacy(
            List<SkillEvolutionAuthoredCandidateOption> options) {
        SkillEvolutionAuthoredCandidate selected = options.stream()
                .sorted(Comparator.comparingInt(option -> option.audit().candidateIndex()))
                .map(SkillEvolutionAuthoredCandidateOption::candidate)
                .filter(SkillEvolutionAuthoredCandidate::reusableChange)
                .findFirst()
                .orElseGet(() -> SkillEvolutionAuthoredCandidate.from(Map.of()));
        return new SkillCandidateSetSelectionResult(
                SkillCandidateSelectionMode.LEGACY_COMPATIBILITY,
                selected,
                null,
                selected.reusableChange()
                        ? "SKILL_LEGACY_FIRST_REUSABLE_SELECTED"
                        : "SKILL_LEGACY_NO_REUSABLE_CANDIDATE");
    }

    private SkillCandidateSetSelectionResult strict(
            List<SkillEvolutionAuthoredCandidateOption> options,
            SkillCandidateTournamentContext context) {
        if (strictTournament == null) {
            throw new IllegalStateException("SKILL_STRICT_TOURNAMENT_NOT_CONFIGURED");
        }
        if (context == null) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_CONTEXT_REQUIRED");
        }
        if (options.size() < 2 || options.size() > 4) {
            throw new IllegalArgumentException("SKILL_STRICT_CANDIDATE_COUNT_INVALID");
        }
        List<SkillTournamentCandidate> candidates = new ArrayList<>();
        Map<String, SkillEvolutionAuthoredCandidate> byId = new LinkedHashMap<>();
        for (SkillEvolutionAuthoredCandidateOption option : options) {
            String candidateId = "candidate-" + option.audit().candidateIndex();
            String candidateHash = CanonicalObjectHasher.sha256(option.candidate().payload());
            int complexity = patchComplexity(option.candidate().payload());
            candidates.add(new SkillTournamentCandidate(
                    candidateId, candidateHash, option.candidate(), complexity));
            byId.put(candidateId, option.candidate());
        }
        SkillCandidateTournamentResult result = strictTournament.run(
                context, candidates);
        SkillEvolutionAuthoredCandidate selected = result.selected()
                ? byId.get(result.selectedCandidateId())
                : SkillEvolutionAuthoredCandidate.from(Map.of());
        return new SkillCandidateSetSelectionResult(
                SkillCandidateSelectionMode.STRICT_TOURNAMENT,
                selected,
                result,
                result.selected()
                        ? "SKILL_STRICT_TOURNAMENT_SELECTED"
                        : result.manualReviewRequired()
                        ? "SKILL_STRICT_MANUAL_REVIEW_REQUIRED"
                        : "SKILL_STRICT_NO_ELIGIBLE_CANDIDATE");
    }

    private int patchComplexity(Map<String, Object> payload) {
        Object changes = payload == null ? null : payload.get("changes");
        if (!(changes instanceof Iterable<?> iterable)) return 0;
        int count = 0;
        for (Object ignored : iterable) count++;
        return count;
    }
}
