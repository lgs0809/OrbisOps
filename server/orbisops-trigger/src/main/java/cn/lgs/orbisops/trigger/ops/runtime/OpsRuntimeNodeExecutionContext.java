package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.List;
import java.util.function.Consumer;

public record OpsRuntimeNodeExecutionContext(NodeExecutor executor,
                                             OpsAgentDefinition definition,
                                             OpsAgentChatRequest request,
                                             OpsRuntimeExecutionPlan plan,
                                             List<OpsRuntimeEvent> events,
                                             Consumer<OpsRuntimeEvent> eventSink) {
    public OpsRuntimeNodeExecutionContext {
        if (executor == null) throw new IllegalArgumentException("RUNTIME_NODE_EXECUTOR_REQUIRED");
        if (definition == null) throw new IllegalArgumentException("RUNTIME_NODE_DEFINITION_REQUIRED");
        if (request == null) throw new IllegalArgumentException("RUNTIME_NODE_REQUEST_REQUIRED");
        if (plan == null) throw new IllegalArgumentException("RUNTIME_NODE_PLAN_REQUIRED");
        events = events == null ? List.of() : events;
    }

    public interface NodeExecutor {
        String executeChat(OpsRuntimeNodeExecutionContext context);

        String executeGraph(OpsRuntimeNodeExecutionContext context);

        String executeAgentScope(OpsRuntimeNodeExecutionContext context);
    }
}
