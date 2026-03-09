package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillOptimizationRun;
import cn.lgs.orbisops.domain.skill.service.SkillOptimizationPolicy;

import java.util.List;

public final class SkillOptimizationRunApplicationService {

    private final SkillOptimizationRunPort port;
    private final SkillOptimizationClockPort clock;
    private final SkillOptimizationPolicy policy;

    public SkillOptimizationRunApplicationService(
            SkillOptimizationRunPort port,
            SkillOptimizationClockPort clock) {
        this(port, clock, new SkillOptimizationPolicy());
    }

    SkillOptimizationRunApplicationService(
            SkillOptimizationRunPort port,
            SkillOptimizationClockPort clock,
            SkillOptimizationPolicy policy) {
        if (port == null || clock == null || policy == null) {
            throw new IllegalArgumentException("SKILL_OPTIMIZATION_RUN_DEPENDENCY_REQUIRED");
        }
        this.port = port;
        this.clock = clock;
        this.policy = policy;
    }

    public SkillOptimizationRun start(
            String runId,
            String skillId,
            long baseVersion,
            String baseSkillHash,
            int maxRounds) {
        return port.save(policy.start(
                runId, skillId, baseVersion, baseSkillHash, maxRounds, clock.now()));
    }

    public SkillOptimizationRun startRound(
            String runId,
            List<String> diagnosisIds) {
        return save(runId, run -> policy.startRound(run, diagnosisIds, clock.now()));
    }

    public SkillOptimizationRun beginEvaluation(
            String runId,
            List<String> candidateIds) {
        return save(runId, run -> policy.beginEvaluation(run, candidateIds, clock.now()));
    }

    public SkillOptimizationRun completeRound(
            String runId,
            List<String> evaluationIds,
            String selectedCandidateId,
            String verifierVersion,
            String reasonCode,
            boolean successful) {
        return save(runId, run -> policy.completeRound(
                run,
                evaluationIds,
                selectedCandidateId,
                verifierVersion,
                reasonCode,
                successful,
                clock.now()));
    }

    public SkillOptimizationRun fail(
            String runId,
            String reasonCode) {
        return save(runId, run -> policy.fail(run, reasonCode, clock.now()));
    }

    public SkillOptimizationRun cancel(String runId) {
        return save(runId, run -> policy.cancel(run, clock.now()));
    }

    public SkillOptimizationRun get(String runId) {
        return port.get(required(runId));
    }

    private SkillOptimizationRun save(
            String runId,
            java.util.function.UnaryOperator<SkillOptimizationRun> transition) {
        SkillOptimizationRun current = port.get(required(runId));
        if (current == null) {
            throw new IllegalArgumentException("SKILL_OPTIMIZATION_RUN_NOT_FOUND:" + runId);
        }
        return port.save(transition.apply(current));
    }

    private String required(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("SKILL_OPTIMIZATION_RUN_ID_REQUIRED");
        return normalized;
    }
}
