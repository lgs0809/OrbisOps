package cn.lgs.orbisops.domain.agentdefinition.rule;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowRouteMode;

@FunctionalInterface
public interface WorkflowRuleCompiler {

    WorkflowRule compile(
            AgentWorkflowRouteMode routeMode,
            String expression);
}
