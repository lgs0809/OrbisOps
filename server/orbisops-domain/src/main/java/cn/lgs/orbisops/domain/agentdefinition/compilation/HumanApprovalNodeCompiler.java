package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;

import java.util.Set;

public final class HumanApprovalNodeCompiler extends AbstractAgentWorkflowNodeCompiler {

    public HumanApprovalNodeCompiler() {
        super("human-approval-node", Set.of(AgentWorkflowNodeType.HUMAN_APPROVAL));
    }

    // The durable approval handler owns execution. Human authority is resolved
    // from the waiting work session, never from a configured model agent.
}
