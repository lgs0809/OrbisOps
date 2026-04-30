package cn.lgs.orbisops.domain.skill.model;

import java.time.Instant;
import java.util.List;

public record SkillDefectDiagnosis(
        String diagnosisId,
        String skillId,
        long skillVersion,
        SkillDefectLayer layer,
        String symptom,
        String rootCause,
        List<String> supportingTrajectoryIds,
        List<String> counterexampleIds,
        double confidence,
        String suggestedDirection,
        Instant diagnosedAt
) {

    public SkillDefectDiagnosis {
        diagnosisId = required(diagnosisId, "SKILL_DIAGNOSIS_ID_REQUIRED");
        skillId = required(skillId, "SKILL_DIAGNOSIS_SKILL_ID_REQUIRED");
        if (skillVersion <= 0) throw new IllegalArgumentException("SKILL_DIAGNOSIS_VERSION_INVALID");
        if (layer == null) throw new IllegalArgumentException("SKILL_DIAGNOSIS_LAYER_REQUIRED");
        symptom = required(symptom, "SKILL_DIAGNOSIS_SYMPTOM_REQUIRED");
        rootCause = required(rootCause, "SKILL_DIAGNOSIS_ROOT_CAUSE_REQUIRED");
        supportingTrajectoryIds = immutable(supportingTrajectoryIds);
        counterexampleIds = immutable(counterexampleIds);
        if (!Double.isFinite(confidence) || confidence < 0D || confidence > 1D) {
            throw new IllegalArgumentException("SKILL_DIAGNOSIS_CONFIDENCE_INVALID");
        }
        suggestedDirection = required(suggestedDirection, "SKILL_DIAGNOSIS_DIRECTION_REQUIRED");
        if (diagnosedAt == null) throw new IllegalArgumentException("SKILL_DIAGNOSIS_TIME_REQUIRED");
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : values.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank()).distinct().sorted().toList();
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
