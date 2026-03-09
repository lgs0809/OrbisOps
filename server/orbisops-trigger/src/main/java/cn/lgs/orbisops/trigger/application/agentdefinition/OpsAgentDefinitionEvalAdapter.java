package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionEvalPort;
import cn.lgs.orbisops.trigger.application.agenteval.OpsAgentEvalAdapter;

import java.util.Map;

/** Trigger adapter joining project admission with the existing Agent Eval application adapter. */
public final class OpsAgentDefinitionEvalAdapter
        implements AgentDefinitionEvalPort<
        Map<String, Object>,
        Map<String, Object>> {

    private final OpsAgentCapabilityApplicationService capabilities;
    private final OpsAgentEvalAdapter evalAdapter;

    public OpsAgentDefinitionEvalAdapter(
            OpsAgentCapabilityApplicationService capabilities,
            OpsAgentEvalAdapter evalAdapter) {
        if (capabilities == null || evalAdapter == null) {
            throw new IllegalArgumentException(
                    "AGENT_DEFINITION_EVAL_ADAPTER_DEPENDENCIES_REQUIRED");
        }
        this.capabilities = capabilities;
        this.evalAdapter = evalAdapter;
    }

    @Override
    public String requireExistingProject(String projectId) {
        return capabilities.requireExistingProject(projectId);
    }

    @Override
    public Map<String, Object> createSuite(
            String projectId,
            String agentId,
            Map<String, Object> request,
            String actor) {
        return evalAdapter.createSuite(projectId, agentId, request, actor);
    }

    @Override
    public Map<String, Object> run(
            String projectId,
            String agentId,
            int version,
            String suiteId,
            String actor) {
        return evalAdapter.run(projectId, agentId, version, suiteId, actor);
    }
}
