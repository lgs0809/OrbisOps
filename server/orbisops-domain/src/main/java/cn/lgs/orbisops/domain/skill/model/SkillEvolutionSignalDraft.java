package cn.lgs.orbisops.domain.skill.model;

/** Typed normalized draft for one Skill Evolution signal. */
public record SkillEvolutionSignalDraft(
        String signalType,
        String projectId,
        String agentId,
        String runId,
        String sessionId,
        String payloadJson) {

    public SkillEvolutionSignalDraft {
        signalType = required(signalType, "SKILL_SIGNAL_TYPE_REQUIRED");
        projectId = value(projectId);
        agentId = value(agentId);
        runId = value(runId);
        sessionId = value(sessionId);
        payloadJson = payloadJson == null || payloadJson.trim().isBlank() ? "{}" : payloadJson.trim();
    }

    private static String required(String value, String code) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(code);
        return normalized;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
