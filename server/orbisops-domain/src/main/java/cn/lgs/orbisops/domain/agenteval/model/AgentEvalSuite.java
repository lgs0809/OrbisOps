package cn.lgs.orbisops.domain.agenteval.model;

import java.util.List;

public record AgentEvalSuite(
        String suiteId,
        String projectId,
        String agentId,
        String name,
        int version,
        List<AgentEvalCase> cases,
        String createdBy) {

    public AgentEvalSuite {
        suiteId = required(suiteId, "AGENT_EVAL_SUITE_ID_REQUIRED");
        projectId = required(projectId, "AGENT_EVAL_PROJECT_REQUIRED");
        agentId = required(agentId, "AGENT_EVAL_AGENT_REQUIRED");
        name = required(name, "AGENT_EVAL_SUITE_NAME_REQUIRED");
        version = Math.max(1, version);
        cases = cases == null ? List.of() : List.copyOf(cases);
        if (cases.isEmpty()) throw new IllegalArgumentException("Agent Eval Suite 至少需要一个可重复用例");
        createdBy = text(createdBy);
    }

    private static String required(String value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
