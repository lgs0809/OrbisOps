package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.application.agenteval.AgentEvalCreateSuiteCommand;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalRunResult;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalSuite;

/** Outbound Agent Eval boundary used by the default Agent bootstrap process. */
public interface ProjectDefaultAgentEvalPort {

    AgentEvalSuite createReleaseSuite(AgentEvalCreateSuiteCommand command);

    AgentEvalRunResult runReleaseEvaluation(
            String projectId,
            String agentId,
            int version,
            String suiteId,
            String actor);

    void assertReleaseAllowed(
            String projectId,
            String agentId,
            int version,
            String definitionHash);
}
