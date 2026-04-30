package cn.lgs.orbisops.application.skill;

/** Typed application command for recording one Skill Evolution signal. */
public record SkillEvolutionSignalCommand(
        String signalType,
        String projectId,
        String agentId,
        String runId,
        String sessionId,
        String payloadJson) {

    public SkillEvolutionSignalCommand {
        signalType = value(signalType);
        projectId = value(projectId);
        agentId = value(agentId);
        runId = value(runId);
        sessionId = value(sessionId);
        payloadJson = payloadJson == null || payloadJson.trim().isBlank() ? "{}" : payloadJson.trim();
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
