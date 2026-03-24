package cn.lgs.orbisops.domain.agenteval.model;

public record AgentEvalEdge(String from, String to) {

    public AgentEvalEdge {
        from = text(from);
        to = text(to);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
