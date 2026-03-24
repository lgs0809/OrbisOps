package cn.lgs.orbisops.domain.agentdefinition.compilation;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowDefinition;

/** External static checks supplied by the Application/ACL boundary. */
public interface AgentWorkflowCompilationHooks {

    void validateCapabilities(AgentWorkflowDefinition definition);

    void validateSecurityBoundary(AgentWorkflowDefinition definition);

    static AgentWorkflowCompilationHooks noop() {
        return new AgentWorkflowCompilationHooks() {
            @Override
            public void validateCapabilities(AgentWorkflowDefinition definition) {
            }

            @Override
            public void validateSecurityBoundary(AgentWorkflowDefinition definition) {
            }
        };
    }
}
