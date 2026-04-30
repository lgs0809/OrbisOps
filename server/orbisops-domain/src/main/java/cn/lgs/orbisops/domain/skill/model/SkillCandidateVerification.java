package cn.lgs.orbisops.domain.skill.model;

public record SkillCandidateVerification(
        String candidateId,
        String candidateHash,
        SkillStructuralVerification structural,
        SkillBehaviorEvaluation behavior,
        SkillModelJudgeEvaluation judge,
        String hiddenEvalHash,
        String mutationEvalHash,
        int patchComplexity
) {

    public SkillCandidateVerification {
        candidateId = required(candidateId, "SKILL_TOURNAMENT_CANDIDATE_ID_REQUIRED");
        candidateHash = required(candidateHash, "SKILL_TOURNAMENT_CANDIDATE_HASH_REQUIRED");
        if (structural == null) throw new IllegalArgumentException("SKILL_TOURNAMENT_STRUCTURAL_REQUIRED");
        hiddenEvalHash = text(hiddenEvalHash);
        mutationEvalHash = text(mutationEvalHash);
        if (patchComplexity < 0) throw new IllegalArgumentException("SKILL_TOURNAMENT_COMPLEXITY_INVALID");
        if (!candidateId.equals(structural.candidateId())) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_STRUCTURAL_CANDIDATE_MISMATCH");
        }
        if (behavior != null && !candidateId.equals(behavior.candidate().activeSkillHash())
                && !candidateHash.equals(behavior.candidate().activeSkillHash())) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_BEHAVIOR_CANDIDATE_MISMATCH");
        }
        if (judge != null && !candidateId.equals(judge.candidateId())) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_JUDGE_CANDIDATE_MISMATCH");
        }
    }

    public boolean deterministicPassed() {
        return structural.passed()
                && behavior != null
                && behavior.admitted()
                && behavior.candidate().metrics().safetyViolationCount() == 0;
    }

    public boolean eligible() {
        return deterministicPassed()
                && judge != null
                && judge.disposition() == SkillModelJudgeDisposition.PASS;
    }

    public boolean manualReviewRequired() {
        return deterministicPassed()
                && (judge == null
                || judge.disposition() == SkillModelJudgeDisposition.UNAVAILABLE
                || judge.disposition() == SkillModelJudgeDisposition.MANUAL_REVIEW);
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
