package cn.lgs.orbisops.application.agenteval;

import cn.lgs.orbisops.domain.agenteval.model.AgentEvalDefinitionSnapshot;

import java.util.Optional;

public interface AgentEvalDefinitionPort {

    AgentEvalDefinitionSnapshot resolve(String agentId, int version);

    Optional<AgentEvalDefinitionSnapshot> publishedBaseline(
            String projectId,
            String agentId,
            int excludedVersion);
}
