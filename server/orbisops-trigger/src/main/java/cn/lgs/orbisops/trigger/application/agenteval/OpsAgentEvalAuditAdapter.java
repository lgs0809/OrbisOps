package cn.lgs.orbisops.trigger.application.agenteval;

import cn.lgs.orbisops.application.agenteval.AgentEvalAuditPort;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalRunResult;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalSuite;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OpsAgentEvalAuditAdapter implements AgentEvalAuditPort {

    private final OpsConfigAuditService audit;

    public OpsAgentEvalAuditAdapter(OpsConfigAuditService audit) {
        this.audit = audit;
    }

    @Override
    public void recordSuiteCreated(AgentEvalSuite suite) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", suite.projectId());
        payload.put("agentId", suite.agentId());
        payload.put("caseCount", suite.cases().size());
        payload.put("actor", suite.createdBy());
        audit.record("agent-eval", "suite-create", suite.suiteId(), null, payload);
    }

    @Override
    public void recordRunCompleted(AgentEvalRunResult result, String actor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", result.projectId());
        payload.put("agentId", result.agentId());
        payload.put("agentVersion", result.agentVersion());
        payload.put("definitionHash", result.definitionHash());
        payload.put("baselineVersion", result.baselineVersion());
        payload.put("regressionStatus", result.regressionStatus());
        payload.put("passedCases", result.passedCases());
        payload.put("failedCases", result.failedCases());
        payload.put("actor", actor == null ? "" : actor.trim());
        audit.record("agent-eval", "run-" + result.status().toLowerCase(java.util.Locale.ROOT),
                result.evalRunId(), null, payload);
    }
}
