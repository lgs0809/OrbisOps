package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillOptimizationRound;
import cn.lgs.orbisops.domain.skill.model.SkillOptimizationRun;
import cn.lgs.orbisops.domain.skill.model.SkillOptimizationStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Bounded Skill optimization state transitions. */
public final class SkillOptimizationPolicy {

    public SkillOptimizationRun start(
            String runId,
            String skillId,
            long baseVersion,
            String baseSkillHash,
            int maxRounds,
            Instant now) {
        return new SkillOptimizationRun(
                runId,
                skillId,
                baseVersion,
                baseSkillHash,
                SkillOptimizationStatus.PLANNED,
                maxRounds,
                List.of(),
                requiredTime(now),
                now);
    }

    public SkillOptimizationRun startRound(
            SkillOptimizationRun run,
            List<String> diagnosisIds,
            Instant now) {
        assertMutable(run);
        if (run.rounds().stream().anyMatch(round -> !round.completed())) {
            throw new IllegalStateException("SKILL_OPTIMIZATION_ACTIVE_ROUND_EXISTS");
        }
        if (run.nextRoundNumber() > run.maxRounds()) {
            throw new IllegalStateException("SKILL_OPTIMIZATION_ROUND_LIMIT_REACHED");
        }
        List<SkillOptimizationRound> rounds = new ArrayList<>(run.rounds());
        rounds.add(new SkillOptimizationRound(
                run.nextRoundNumber(),
                diagnosisIds,
                List.of(),
                List.of(),
                "",
                "",
                "ROUND_STARTED",
                requiredTime(now),
                null));
        return copy(run, SkillOptimizationStatus.RUNNING, rounds, now);
    }

    public SkillOptimizationRun beginEvaluation(
            SkillOptimizationRun run,
            List<String> candidateIds,
            Instant now) {
        assertMutable(run);
        SkillOptimizationRound active = activeRound(run);
        if (candidateIds == null || candidateIds.isEmpty()) {
            throw new IllegalArgumentException("SKILL_OPTIMIZATION_CANDIDATES_REQUIRED");
        }
        List<SkillOptimizationRound> rounds = replaceLast(run.rounds(), new SkillOptimizationRound(
                active.round(),
                active.diagnosisIds(),
                candidateIds,
                List.of(),
                "",
                "",
                "CANDIDATES_AUTHORED",
                active.startedAt(),
                null));
        return copy(run, SkillOptimizationStatus.EVALUATING, rounds, now);
    }

    public SkillOptimizationRun completeRound(
            SkillOptimizationRun run,
            List<String> evaluationIds,
            String selectedCandidateId,
            String verifierVersion,
            String reasonCode,
            boolean successful,
            Instant now) {
        assertMutable(run);
        SkillOptimizationRound active = activeRound(run);
        if (active.candidateIds().isEmpty()) {
            throw new IllegalStateException("SKILL_OPTIMIZATION_CANDIDATES_MISSING");
        }
        if (evaluationIds == null || evaluationIds.isEmpty()) {
            throw new IllegalArgumentException("SKILL_OPTIMIZATION_EVALUATIONS_REQUIRED");
        }
        String selected = text(selectedCandidateId);
        if (successful && (selected.isBlank() || !active.candidateIds().contains(selected))) {
            throw new IllegalArgumentException("SKILL_OPTIMIZATION_SELECTED_CANDIDATE_INVALID");
        }
        String verifier = required(verifierVersion, "SKILL_OPTIMIZATION_VERIFIER_REQUIRED");
        String reason = required(reasonCode, "SKILL_OPTIMIZATION_REASON_REQUIRED");
        List<SkillOptimizationRound> rounds = replaceLast(run.rounds(), new SkillOptimizationRound(
                active.round(),
                active.diagnosisIds(),
                active.candidateIds(),
                evaluationIds,
                selected,
                verifier,
                reason,
                active.startedAt(),
                requiredTime(now)));
        SkillOptimizationStatus status;
        if (successful) {
            status = SkillOptimizationStatus.SUCCEEDED;
        } else if (rounds.size() >= run.maxRounds()) {
            status = SkillOptimizationStatus.ROUND_LIMIT_REACHED;
        } else {
            status = SkillOptimizationStatus.RUNNING;
        }
        return copy(run, status, rounds, now);
    }

    public SkillOptimizationRun fail(
            SkillOptimizationRun run,
            String reasonCode,
            Instant now) {
        assertMutable(run);
        required(reasonCode, "SKILL_OPTIMIZATION_REASON_REQUIRED");
        return copy(run, SkillOptimizationStatus.FAILED, run.rounds(), now);
    }

    public SkillOptimizationRun cancel(
            SkillOptimizationRun run,
            Instant now) {
        if (run == null) throw new IllegalArgumentException("SKILL_OPTIMIZATION_RUN_REQUIRED");
        if (run.status().terminal()) return run;
        return copy(run, SkillOptimizationStatus.CANCELED, run.rounds(), now);
    }

    private SkillOptimizationRound activeRound(SkillOptimizationRun run) {
        if (run.rounds().isEmpty()) {
            throw new IllegalStateException("SKILL_OPTIMIZATION_ACTIVE_ROUND_REQUIRED");
        }
        SkillOptimizationRound round = run.rounds().get(run.rounds().size() - 1);
        if (round.completed()) {
            throw new IllegalStateException("SKILL_OPTIMIZATION_ACTIVE_ROUND_REQUIRED");
        }
        return round;
    }

    private SkillOptimizationRun copy(
            SkillOptimizationRun run,
            SkillOptimizationStatus status,
            List<SkillOptimizationRound> rounds,
            Instant now) {
        return new SkillOptimizationRun(
                run.runId(),
                run.skillId(),
                run.baseVersion(),
                run.baseSkillHash(),
                status,
                run.maxRounds(),
                rounds,
                run.createdAt(),
                requiredTime(now));
    }

    private List<SkillOptimizationRound> replaceLast(
            List<SkillOptimizationRound> source,
            SkillOptimizationRound replacement) {
        List<SkillOptimizationRound> result = new ArrayList<>(source);
        result.set(result.size() - 1, replacement);
        return List.copyOf(result);
    }

    private void assertMutable(SkillOptimizationRun run) {
        if (run == null) throw new IllegalArgumentException("SKILL_OPTIMIZATION_RUN_REQUIRED");
        if (run.status().terminal()) {
            throw new IllegalStateException("SKILL_OPTIMIZATION_RUN_TERMINAL:" + run.status());
        }
    }

    private Instant requiredTime(Instant value) {
        if (value == null) throw new IllegalArgumentException("SKILL_OPTIMIZATION_TIME_REQUIRED");
        return value;
    }

    private String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
