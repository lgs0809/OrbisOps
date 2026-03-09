package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

public record SkillModelJudgeEvaluation(
        String candidateId,
        SkillModelJudgeDisposition disposition,
        double score,
        List<String> reasonCodes,
        String judgeVersion
) {

    public SkillModelJudgeEvaluation {
        candidateId = required(candidateId, "SKILL_JUDGE_CANDIDATE_ID_REQUIRED");
        if (disposition == null) throw new IllegalArgumentException("SKILL_JUDGE_DISPOSITION_REQUIRED");
        if (!Double.isFinite(score) || score < 0D || score > 1D) {
            throw new IllegalArgumentException("SKILL_JUDGE_SCORE_INVALID");
        }
        reasonCodes = reasonCodes == null ? List.of() : reasonCodes.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank()).distinct().sorted().toList();
        judgeVersion = required(judgeVersion, "SKILL_JUDGE_VERSION_REQUIRED");
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
