package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.List;
import java.util.function.Consumer;

public interface OpsEngineAdapter {

    String key();

    String execute(OpsAgentDefinition definition,
                   OpsAgentChatRequest request,
                   OpsRuntimeExecutionPlan plan,
                   OpsAgentRuntimeSupport runtime,
                   List<OpsRuntimeEvent> events,
                   Consumer<OpsRuntimeEvent> eventSink);

}
