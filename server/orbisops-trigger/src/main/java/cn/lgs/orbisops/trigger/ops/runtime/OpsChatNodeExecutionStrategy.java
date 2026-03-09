package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.worksession.NodeExecutionStrategy;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class OpsChatNodeExecutionStrategy implements NodeExecutionStrategy<OpsRuntimeExecutionNode, OpsRuntimeNodeExecutionContext, String> {

    @Override
    public String strategyId() {
        return "spring-ai-chat";
    }

    @Override
    public Set<String> supportedNodeTypes() {
        return Set.of(OpsRuntimeExecutionNode.CHAT.name());
    }

    @Override
    public String execute(OpsRuntimeExecutionNode node, OpsRuntimeNodeExecutionContext context) {
        if (node != OpsRuntimeExecutionNode.CHAT) throw new IllegalArgumentException("CHAT_NODE_STRATEGY_MISMATCH");
        return context.executor().executeChat(context);
    }
}
