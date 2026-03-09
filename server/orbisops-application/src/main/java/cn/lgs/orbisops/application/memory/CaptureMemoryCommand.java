package cn.lgs.orbisops.application.memory;

public record CaptureMemoryCommand(String query,
                                   String userId,
                                   String projectId,
                                   String agentId,
                                   String sessionId,
                                   String runId,
                                   String riskLevel) {

    public CaptureMemoryCommand {
        query = value(query);
        userId = value(userId);
        projectId = value(projectId);
        agentId = value(agentId);
        sessionId = value(sessionId);
        runId = value(runId);
        riskLevel = value(riskLevel);
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
