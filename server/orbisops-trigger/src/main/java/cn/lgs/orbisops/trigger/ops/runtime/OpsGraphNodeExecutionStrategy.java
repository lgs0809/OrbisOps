package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.worksession.NodeExecutionStrategy;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class OpsGraphNodeExecutionStrategy implements NodeExecutionStrategy<OpsRuntimeExecutionNode, OpsRuntimeNodeExecutionContext, String> {

    @Override
    public String strategyId() {
        return "spring-ai-alibaba-graph";
    }

    @Override
    public Set<String> supportedNodeTypes() {
        return Set.of(OpsRuntimeExecutionNode.GRAPH.name());
    }

    @Override
    public String execute(OpsRuntimeExecutionNode node, OpsRuntimeNodeExecutionContext context) {
        if (node != OpsRuntimeExecutionNode.GRAPH) throw new IllegalArgumentException("GRAPH_NODE_STRATEGY_MISMATCH");
        return context.executor().executeGraph(context);
    }
}
