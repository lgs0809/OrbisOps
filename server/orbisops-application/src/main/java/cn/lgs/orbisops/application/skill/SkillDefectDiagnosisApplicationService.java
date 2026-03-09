package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillDefectDiagnosis;

import java.util.List;

public final class SkillDefectDiagnosisApplicationService {

    private static final int MAX_QUERY_LIMIT = 200;

    private final SkillDefectDiagnosisPort port;
    private final SkillOptimizationClockPort clock;

    public SkillDefectDiagnosisApplicationService(
            SkillDefectDiagnosisPort port,
            SkillOptimizationClockPort clock) {
        if (port == null || clock == null) {
            throw new IllegalArgumentException("SKILL_DIAGNOSIS_DEPENDENCY_REQUIRED");
        }
        this.port = port;
        this.clock = clock;
    }

    public SkillDefectDiagnosis record(SkillDefectDiagnosisCommand command) {
        if (command == null) throw new IllegalArgumentException("SKILL_DIAGNOSIS_COMMAND_REQUIRED");
        return port.save(new SkillDefectDiagnosis(
                command.diagnosisId(),
                command.skillId(),
                command.skillVersion(),
                command.layer(),
                command.symptom(),
                command.rootCause(),
                command.supportingTrajectoryIds(),
                command.counterexampleIds(),
                command.confidence(),
                command.suggestedDirection(),
                clock.now()));
    }

    public List<SkillDefectDiagnosis> diagnoses(
            String skillId,
            long skillVersion,
            int limit) {
        String id = required(skillId, "SKILL_DIAGNOSIS_SKILL_ID_REQUIRED");
        if (skillVersion <= 0) throw new IllegalArgumentException("SKILL_DIAGNOSIS_VERSION_INVALID");
        return List.copyOf(port.findBySkill(id, skillVersion, bounded(limit)));
    }

    private int bounded(int limit) {
        if (limit <= 0) return 20;
        return Math.min(limit, MAX_QUERY_LIMIT);
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
