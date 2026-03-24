package cn.lgs.orbisops.domain.agenteval.adapter.repository;

import cn.lgs.orbisops.domain.agenteval.model.AgentEvalRunResult;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalRunStart;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalSuite;

import java.time.Instant;
import java.util.Optional;

public interface IAgentEvalRepository {

    AgentEvalSuite saveSuite(AgentEvalSuite suite);

    Optional<AgentEvalSuite> findSuite(String suiteId, String projectId);

    void saveRun(AgentEvalRunStart run, AgentEvalRunResult result, Instant finishedAt);

    boolean hasPassedReleaseGate(String projectId, String agentId, int version, String definitionHash);
}
