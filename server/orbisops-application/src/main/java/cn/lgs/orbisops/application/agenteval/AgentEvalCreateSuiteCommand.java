package cn.lgs.orbisops.application.agenteval;

import cn.lgs.orbisops.domain.agenteval.model.AgentEvalCase;

import java.util.List;

public record AgentEvalCreateSuiteCommand(
        String suiteId,
        String projectId,
        String agentId,
        String name,
        List<AgentEvalCase> cases,
        String actor) {

    public AgentEvalCreateSuiteCommand {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}
