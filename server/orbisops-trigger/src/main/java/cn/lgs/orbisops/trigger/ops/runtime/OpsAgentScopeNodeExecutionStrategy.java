package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.worksession.NodeExecutionStrategy;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class OpsAgentScopeNodeExecutionStrategy implements NodeExecutionStrategy<OpsRuntimeExecutionNode, OpsRuntimeNodeExecutionContext, String> {

    @Override
    public String strategyId() {
        return "spring-ai-alibaba-agentscope";
    }

    @Override
    public Set<String> supportedNodeTypes() {
        return Set.of(OpsRuntimeExecutionNode.AGENT_SCOPE.name());
    }

    @Override
    public String execute(OpsRuntimeExecutionNode node, OpsRuntimeNodeExecutionContext context) {
        if (node != OpsRuntimeExecutionNode.AGENT_SCOPE) {
            throw new IllegalArgumentException("AGENT_SCOPE_NODE_STRATEGY_MISMATCH");
        }
        return context.executor().executeAgentScope(context);
    }
}
