package cn.lgs.orbisops.application.agenteval;

import cn.lgs.orbisops.domain.agenteval.model.AgentEvalRunResult;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalSuite;

public interface AgentEvalAuditPort {

    void recordSuiteCreated(AgentEvalSuite suite);

    void recordRunCompleted(AgentEvalRunResult result, String actor);
}
