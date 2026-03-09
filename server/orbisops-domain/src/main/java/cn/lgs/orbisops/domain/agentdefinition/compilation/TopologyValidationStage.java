package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.service.AgentGraphDefinitionPolicy;

public final class TopologyValidationStage extends AbstractWorkflowCompilationStage {

    public static final String STAGE_ID = "topology-validation";

    private final AgentGraphDefinitionPolicy policy;

    public TopologyValidationStage(AgentGraphDefinitionPolicy policy) {
        super(STAGE_ID, 400, WorkflowCompilationErrorCode.TOPOLOGY_INVALID);
        if (policy == null) throw new IllegalArgumentException("AGENT_GRAPH_DEFINITION_POLICY_REQUIRED");
        this.policy = policy;
    }

    @Override
    public void compile(AgentWorkflowCompilationContext context) {
        policy.validate(context.definition().graph());
    }
}
