package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

public record SkillBehaviorEvaluation(
        String evaluationId,
        SkillBehaviorReplayResult noSkill,
        SkillBehaviorReplayResult baseline,
        SkillBehaviorReplayResult candidate,
        boolean admitted,
        List<String> reasonCodes,
        String verifierVersion
) {

    public SkillBehaviorEvaluation {
        evaluationId = required(evaluationId, "SKILL_REPLAY_EVALUATION_ID_REQUIRED");
        if (noSkill == null || baseline == null || candidate == null) {
            throw new IllegalArgumentException("SKILL_REPLAY_THREE_ARMS_REQUIRED");
        }
        if (noSkill.arm() != SkillBehaviorReplayArm.NO_SKILL
                || baseline.arm() != SkillBehaviorReplayArm.BASELINE
                || candidate.arm() != SkillBehaviorReplayArm.CANDIDATE) {
            throw new IllegalArgumentException("SKILL_REPLAY_ARM_ORDER_INVALID");
        }
        reasonCodes = reasonCodes == null ? List.of() : reasonCodes.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank()).distinct().sorted().toList();
        verifierVersion = required(verifierVersion, "SKILL_REPLAY_VERIFIER_VERSION_REQUIRED");
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
