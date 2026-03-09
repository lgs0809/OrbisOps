package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/** The single product-level Agent runtime adapter. AgentScope/ReAct remain graph node types. */
@Component
public final class OpsUnifiedAgentEngineAdapter extends AbstractOpsEngineAdapter {

    public static final String KEY = "UNIFIED_STATE_GRAPH";

    public OpsUnifiedAgentEngineAdapter() {
        super(KEY);
    }

    @Override
    protected String doExecute(
            OpsAgentDefinition definition,
            OpsAgentChatRequest request,
            OpsRuntimeExecutionPlan plan,
            OpsAgentRuntimeSupport runtime,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        return runtime.runGraphEngine(definition, request, plan, events, eventSink);
    }
}
