package cn.lgs.orbisops.domain.agentdefinition.model;

/** Minimal node facts needed to determine an Agent Definition execution engine. */
public record AgentExecutionNodeFact(
        String type,
        String mode,
        String subEngine) {

    public AgentExecutionNodeFact {
        type = text(type);
        mode = text(mode);
        subEngine = text(subEngine);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
