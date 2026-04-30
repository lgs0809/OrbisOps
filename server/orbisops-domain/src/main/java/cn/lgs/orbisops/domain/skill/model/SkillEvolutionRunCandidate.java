package cn.lgs.orbisops.domain.skill.model;

import java.time.Instant;

/** Completed foreground Run that has not yet been admitted to background Skill learning. */
public record SkillEvolutionRunCandidate(
        String runId,
        String sessionId,
        String projectId,
        String agentId,
        String terminalStatus,
        Instant completedAt) {

    public SkillEvolutionRunCandidate {
        runId = required(runId, "SKILL_EVOLUTION_RUN_ID_REQUIRED");
        sessionId = text(sessionId);
        projectId = required(projectId, "SKILL_EVOLUTION_PROJECT_ID_REQUIRED");
        agentId = text(agentId);
        terminalStatus = required(terminalStatus, "SKILL_EVOLUTION_TERMINAL_STATUS_REQUIRED");
        if (completedAt == null) throw new IllegalArgumentException("SKILL_EVOLUTION_COMPLETED_AT_REQUIRED");
    }

    private static String required(String value, String code) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(code);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
