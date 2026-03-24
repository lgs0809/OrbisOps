package cn.lgs.orbisops.domain.agentdefinition.model;

/** Normalized execution shape of one Agent Definition. */
public record AgentExecutionShapeDecision(
        String engine,
        boolean clearLegacyAgentScope) {

    public AgentExecutionShapeDecision {
        if (engine == null || engine.isBlank()) {
            throw new IllegalArgumentException("AGENT_EXECUTION_ENGINE_REQUIRED");
        }
        engine = engine.trim();
    }
}
